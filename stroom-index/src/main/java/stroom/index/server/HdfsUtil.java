package stroom.index.server;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import stroom.index.shared.IndexShard;

import java.io.IOException;
import java.security.PrivilegedExceptionAction;
import java.util.Optional;
import java.util.function.Supplier;

public class HdfsUtil {
    public static Configuration getConfiguration(final Optional<String> optionalURI) {
        final Configuration configuration = new Configuration();

        optionalURI.map(String::trim).filter(uri -> uri.length() > 0).ifPresent(uri -> configuration.set("fs.defaultFS", uri));

//        conf.set("fs.automatic.close", "true");
//        conf.set("fs.hdfs.impl", org.apache.hadoop.hdfs.DistributedFileSystem.class.getName());
        // conf.set("fs.file.impl",
        // org.apache.hadoop.fs.LocalFileSystem.class.getName());
        configuration.set("dfs.client.use.datanode.hostname", "true");

        return configuration;
    }

    public static <R> R runAs(final Supplier<R> supplier, final Optional<String>  optionalUser) {
        try {
            return buildRemoteUser(optionalUser.map(String::trim)
                    .filter(user -> user.length() > 0)).doAs((PrivilegedExceptionAction<R>) supplier::get);

        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final IOException ioe) {
            throw new RuntimeException(ioe);
        }

        return null;
    }

    private static UserGroupInformation buildRemoteUser(final Optional<String> optionalUser) {
        final String user = optionalUser.orElseGet(() -> {
            try {
                return UserGroupInformation.getCurrentUser().getUserName();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // userGroupInformation =
        // UserGroupInformation.createProxyUser(runAsUser,
        // UserGroupInformation.getLoginUser());
        return UserGroupInformation.createRemoteUser(user);
    }

    public static String toSafePath(final String path) {
        return path.replaceAll(":", "-");
    }
}
