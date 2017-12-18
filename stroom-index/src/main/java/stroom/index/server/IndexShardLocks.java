package stroom.index.server;

import org.apache.lucene.store.Lock;
import org.apache.lucene.store.LockFactory;
import stroom.index.shared.IndexShard;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class IndexShardLocks {
    private static final Set<String> LOCKS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static LockFactory getLockFactory(final IndexShard indexShard) {
        return new IndexShardLockFactory(indexShard.getId());
    }

    private static class IndexShardLockFactory extends LockFactory {
        private final long indexShardId;

        IndexShardLockFactory(long indexShardId) {
            this.indexShardId = indexShardId;
        }

        @Override
        public Lock makeLock(String lockName) {
            if (this.lockPrefix != null) {
                lockName = this.lockPrefix + "-" + lockName;
            }
            lockName = indexShardId + ": " + lockName;

            return new IndexShardLock(LOCKS, lockName);
        }

        @Override
        public void clearLock(String lockName) {
            if (this.lockPrefix != null) {
                lockName = this.lockPrefix + "-" + lockName;
            }
            lockName = indexShardId + ": " + lockName;

            LOCKS.remove(lockName);
        }
    }

    private static class IndexShardLock extends Lock {
        private final String lockName;
        private final Set<String> locks;

        IndexShardLock(final Set<String> locks, final String lockName) {
            this.locks = locks;
            this.lockName = lockName;
        }

        public boolean obtain() {
            return this.locks.add(this.lockName);
        }

        public void release() {
            this.locks.remove(this.lockName);
        }

        public boolean isLocked() {
            return this.locks.contains(this.lockName);
        }

        public String toString() {
            return super.toString() + ": " + this.lockName;
        }
    }
}
