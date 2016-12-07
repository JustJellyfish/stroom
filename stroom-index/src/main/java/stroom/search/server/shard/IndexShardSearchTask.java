/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.search.server.shard;

import org.apache.lucene.search.Query;
import stroom.pipeline.server.errorhandler.ErrorReceiver;
import stroom.search.server.ClusterSearchTask;
import stroom.task.server.ThreadPoolImpl;
import stroom.util.shared.ThreadPool;
import stroom.util.shared.VoidResult;
import stroom.util.task.ServerTask;

import java.util.concurrent.atomic.AtomicLong;

public class IndexShardSearchTask extends ServerTask<VoidResult> {
    public static final ThreadPool THREAD_POOL = new ThreadPoolImpl("Stroom Search Index Shard", 5, 0, Integer.MAX_VALUE);
    private final ClusterSearchTask clusterSearchTask;
    private final Query query;
    private final long indexShardId;
    private final String[] fieldNames;
    private final ResultReceiver resultReceiver;
    private final ErrorReceiver errorReceiver;
    private final AtomicLong hitCount;
    private int shardNumber;
    private int shardTotal;

    public IndexShardSearchTask(final ClusterSearchTask clusterSearchTask, final Query query,
                                final long indexShardId, final String[] fieldNames, final ResultReceiver resultReceiver,
                                final ErrorReceiver errorReceiver, final AtomicLong hitCount) {
        super(clusterSearchTask);
        this.clusterSearchTask = clusterSearchTask;
        this.query = query;
        this.indexShardId = indexShardId;
        this.fieldNames = fieldNames;
        this.resultReceiver = resultReceiver;
        this.errorReceiver = errorReceiver;
        this.hitCount = hitCount;
    }

    public ClusterSearchTask getClusterSearchTask() {
        return clusterSearchTask;
    }

    public Query getQuery() {
        return query;
    }

    public long getIndexShardId() {
        return indexShardId;
    }

    public String[] getFieldNames() {
        return fieldNames;
    }

    public ResultReceiver getResultReceiver() {
        return resultReceiver;
    }

    public ErrorReceiver getErrorReceiver() {
        return errorReceiver;
    }

    public AtomicLong getHitCount() {
        return hitCount;
    }

    public int getShardNumber() {
        return shardNumber;
    }

    public void setShardNumber(final int shardNumber) {
        this.shardNumber = shardNumber;
    }

    public int getShardTotal() {
        return shardTotal;
    }

    public void setShardTotal(final int shardTotal) {
        this.shardTotal = shardTotal;
    }

    @Override
    public ThreadPool getThreadPool() {
        return THREAD_POOL;
    }

    public interface ResultReceiver {
        void receive(long shardId, String[] values);

        void complete(long shardId);
    }
}
