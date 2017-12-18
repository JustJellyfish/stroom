package stroom.index.server;

import com.bah.lucene.BlockCacheDirectoryFactoryV2;
import com.bah.lucene.hdfs.HdfsDirectory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.index.shared.IndexShard;
import stroom.node.shared.Volume;
import stroom.search.server.SearchException;
import stroom.streamstore.server.fs.FileSystemUtil;
import stroom.util.io.FileUtil;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class IndexShardDirectoryFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexShardDirectoryFactory.class);

    private final IndexShard indexShard;

    public IndexShardDirectoryFactory(final IndexShard indexShard) {
        this.indexShard = indexShard;
    }

    public Directory getOrCreateDirectory() throws IOException {
        final String path = IndexShardUtil.getIndexPath(indexShard);

        if (Volume.VolumeType.HDFS.equals(indexShard.getVolume().getVolumeType())) {
            return runAs(() -> {
                try {
                    final Volume volume = indexShard.getVolume();
                    final Configuration configuration = HdfsUtil.getConfiguration(Optional.ofNullable(volume.getHdfsUri()));
                    final URI uri = URI.create(volume.getHdfsUri());
                    final FileSystem hdfs = FileSystem.get(uri, configuration);
                    final Path hdfsPath = new Path(createHdfsPath(indexShard));

                    // Create the directory if it doesn't exist
                    if (hdfs.exists(hdfsPath)) {
                        try {
                            if (!hdfs.listFiles(hdfsPath, false).hasNext()) {
                                throw new IndexException("Unable to find any index shard data in directory: " + path);
                            }
                        } catch (final IOException e) {
                            LOGGER.error(e.getMessage(), e);
                            throw new IndexException("Unable to find any index shard data in directory: " + path, e);
                        }
                    } else {
                        // Make sure the index hasn't been deleted.
                        if (indexShard.getDocumentCount() > 0) {
                            throw new IndexException("Unable to find any index shard data in directory: " + path);
                        }

                        try {
                            hdfs.mkdirs(hdfsPath, FsPermission.getDefault());
                        } catch (final IOException e) {
                            LOGGER.error(e.getMessage(), e);
                            throw new IndexException("Unable to create directories for new index in \"" + path + "\"", e);
                        }
                    }

                    final HdfsDirectory hdfsDirectory = new HdfsDirectory(configuration, hdfsPath);
                    final BlockCacheDirectoryFactoryV2 directoryFactory = new BlockCacheDirectoryFactoryV2(configuration, 1000000);
                    return directoryFactory.newDirectory("index", "shard" + indexShard.getId(), hdfsDirectory, null);
                } catch (final IOException e) {
                    LOGGER.error(e.getMessage(), e);
                    throw new RuntimeException(e.getMessage(), e);
                }
            });

        } else {
            // Open the index writer.
            // If we already have a directory then this is an existing index.
            // Find the index shard dir.
            final File dir = new File(path);
            final String canonicalPath = FileUtil.getCanonicalPath(dir);
            final java.nio.file.Path p = dir.toPath();
            if (Files.isDirectory(p)) {
                try (final Stream<java.nio.file.Path> stream = Files.list(p)) {
                    final long count = stream.count();
                    if (count == 0) {
                        throw new IndexException("Unable to find any index shard data in directory: " + canonicalPath);
                    }
                } catch (final IOException e) {
                    LOGGER.error(e.getMessage(), e);
                    throw new IndexException("Unable to find any index shard data in directory: " + canonicalPath, e);
                }

            } else {
                // Make sure the index hasn't been deleted.
                if (indexShard.getDocumentCount() > 0) {
                    throw new IndexException("Unable to find any index shard data in directory: " + canonicalPath);
                }

                // Try and make all required directories.
                try {
                    Files.createDirectories(p);
                } catch (final IOException e) {
                    LOGGER.error(e.getMessage(), e);
                    throw new IndexException("Unable to create directories for new index in \"" + canonicalPath + "\"", e);
                }
            }

            // Create lucene directory object.
            return new NIOFSDirectory(dir, IndexShardLocks.getLockFactory(indexShard));
        }
    }

    public Directory getDirectory() throws IOException {
        final String path = IndexShardUtil.getIndexPath(indexShard);

        if (Volume.VolumeType.HDFS.equals(indexShard.getVolume().getVolumeType())) {
            return runAs(() -> {
                try {
                    final Volume volume = indexShard.getVolume();
                    final Configuration configuration = HdfsUtil.getConfiguration(Optional.ofNullable(volume.getHdfsUri()));
                    final URI uri = URI.create(volume.getHdfsUri());
                    final FileSystem hdfs = FileSystem.get(uri, configuration);
                    final Path hdfsPath = new Path(createHdfsPath(indexShard));

                    if (!hdfs.exists(hdfsPath)) {
                        throw new SearchException("Index directory not found for searching: " + path);
                    }

                    final HdfsDirectory hdfsDirectory = new HdfsDirectory(configuration, hdfsPath);
                    final BlockCacheDirectoryFactoryV2 directoryFactory = new BlockCacheDirectoryFactoryV2(configuration, 1000000);
                    return directoryFactory.newDirectory("index", "shard" + indexShard.getId(), hdfsDirectory, null);
                } catch (final IOException e) {
                    LOGGER.error(e.getMessage(), e);
                    throw new RuntimeException(e.getMessage(), e);
                }
            });

        } else {
            final File dir = new File(path);
            if (!dir.isDirectory()) {
                throw new SearchException("Index directory not found for searching: " + dir.getAbsolutePath());
            }

            return new NIOFSDirectory(dir, IndexShardLocks.getLockFactory(indexShard));
        }
    }

//    public void clean() throws IOException {
//        final String path = IndexShardUtil.getIndexPath(indexShard);
//
//        if (Volume.VolumeType.HDFS.equals(indexShard.getVolume().getVolumeType())) {
//            final Configuration configuration = getHdfsConfiguration();
//            final FileSystem hdfs = getHDFS(configuration);
//            final Path hdfsPath = toHdfsPath(path);
//
//            // Create the directory if it doesn't exist
//            if (hdfs.exists(hdfsPath)) {
//                final RemoteIterator<LocatedFileStatus> list = hdfs.listFiles(hdfsPath, false);
//                while (list.hasNext()) {
//                    final LocatedFileStatus locatedFileStatus = list.next();
//                    if (locatedFileStatus.getPath().toString().endsWith(".lock")) {
//                        hdfs.delete(locatedFileStatus.getPath(), false);
//                    }
//                }
//            }
//
//        } else {
//            final java.nio.file.Path dir = Paths.get(path);
//
//            // Delete any lingering lock files from previous uses of the index shard.
//            if (Files.isDirectory(dir)) {
//                try (final Stream<java.nio.file.Path> stream = Files.list(dir)) {
//                    stream
//                            .filter(p -> {
//                                final String name = p.getFileName().toString();
//                                return name.endsWith(".lock");
//                            })
//                            .forEach(p -> {
//                                try {
//                                    Files.deleteIfExists(p);
//                                } catch (final IOException e) {
//                                    LOGGER.error(e.getMessage(), e);
//                                }
//                            });
//                } catch (final IOException e) {
//                    LOGGER.error(e.getMessage(), e);
//                }
//            }
//        }
//    }


    public boolean deleteDirectory() {
        final String path = IndexShardUtil.getIndexPath(indexShard);

        if (Volume.VolumeType.HDFS.equals(indexShard.getVolume().getVolumeType())) {
            final Boolean result = runAs(() -> {
                try {
                    final Volume volume = indexShard.getVolume();
                    final Configuration configuration = HdfsUtil.getConfiguration(Optional.ofNullable(volume.getHdfsUri()));
                    final URI uri = URI.create(volume.getHdfsUri());
                    final FileSystem hdfs = FileSystem.get(uri, configuration);
                    final Path hdfsPath = new Path(createHdfsPath(indexShard));

                    return !hdfs.exists(hdfsPath) || hdfs.delete(hdfsPath, true);

                } catch (final IOException e) {
                    LOGGER.error(e.getMessage(), e);
                    throw new RuntimeException(e.getMessage(), e);
                }
            });

            if (result != null) {
                return result;
            }
        } else {
            final java.nio.file.Path dir = Paths.get(path);
            return !Files.isDirectory(dir) || FileSystemUtil.deleteDirectory(dir);
        }

        return false;
    }

    /**
     * HDFS does not support some chars in its dir/filenames so need to clean
     * them here
     *
     * @return A cleaned path
     */
    private String createHdfsPath(final IndexShard indexShard) {
        String path = IndexShardUtil.getIndexPath(indexShard);
        path = HdfsUtil.toSafePath(path);
        return path;
    }

    private <R> R runAs(final Supplier<R> supplier) {
        return HdfsUtil.runAs(supplier, Optional.ofNullable(indexShard.getVolume().getRunAs()));
    }
}
