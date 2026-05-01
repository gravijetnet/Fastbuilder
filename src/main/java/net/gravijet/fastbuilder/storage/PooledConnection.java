package net.gravijet.fastbuilder.storage;

import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * A {@link Connection} wrapper that returns itself to the pool on close() instead of
 * actually closing the underlying connection.
 */
class PooledConnection implements Connection {

    private final Connection delegate;
    private final int        poolIndex;
    private final boolean[]  inUse;
    private final Object     poolLock;

    PooledConnection(Connection delegate, int poolIndex, boolean[] inUse, Object poolLock) {
        this.delegate  = delegate;
        this.poolIndex = poolIndex;
        this.inUse     = inUse;
        this.poolLock  = poolLock;
    }

    @Override
    public void close() {
        synchronized (poolLock) {
            inUse[poolIndex] = false;
        }
    }

    @Override public PreparedStatement prepareStatement(String s) throws SQLException { return delegate.prepareStatement(s); }
    @Override public Statement createStatement() throws SQLException { return delegate.createStatement(); }
    @Override public void setAutoCommit(boolean b) throws SQLException { delegate.setAutoCommit(b); }
    @Override public boolean getAutoCommit() throws SQLException { return delegate.getAutoCommit(); }
    @Override public void commit() throws SQLException { delegate.commit(); }
    @Override public void rollback() throws SQLException { delegate.rollback(); }
    @Override public boolean isClosed() throws SQLException { return delegate.isClosed(); }
    @Override public boolean isValid(int timeout) throws SQLException { return delegate.isValid(timeout); }
    @Override public DatabaseMetaData getMetaData() throws SQLException { return delegate.getMetaData(); }
    @Override public <T> T unwrap(Class<T> c) throws SQLException { return delegate.unwrap(c); }
    @Override public boolean isWrapperFor(Class<?> c) throws SQLException { return delegate.isWrapperFor(c); }
    @Override public PreparedStatement prepareStatement(String s, int a, int b) throws SQLException { return delegate.prepareStatement(s, a, b); }
    @Override public PreparedStatement prepareStatement(String s, int a, int b, int c) throws SQLException { return delegate.prepareStatement(s, a, b, c); }
    @Override public PreparedStatement prepareStatement(String s, int[] c) throws SQLException { return delegate.prepareStatement(s, c); }
    @Override public PreparedStatement prepareStatement(String s, String[] c) throws SQLException { return delegate.prepareStatement(s, c); }
    @Override public PreparedStatement prepareStatement(String s, int c) throws SQLException { return delegate.prepareStatement(s, c); }
    @Override public CallableStatement prepareCall(String s) throws SQLException { return delegate.prepareCall(s); }
    @Override public CallableStatement prepareCall(String s, int a, int b) throws SQLException { return delegate.prepareCall(s, a, b); }
    @Override public CallableStatement prepareCall(String s, int a, int b, int c) throws SQLException { return delegate.prepareCall(s, a, b, c); }
    @Override public String nativeSQL(String s) throws SQLException { return delegate.nativeSQL(s); }
    @Override public void setReadOnly(boolean b) throws SQLException { delegate.setReadOnly(b); }
    @Override public boolean isReadOnly() throws SQLException { return delegate.isReadOnly(); }
    @Override public void setCatalog(String s) throws SQLException { delegate.setCatalog(s); }
    @Override public String getCatalog() throws SQLException { return delegate.getCatalog(); }
    @Override public void setTransactionIsolation(int l) throws SQLException { delegate.setTransactionIsolation(l); }
    @Override public int getTransactionIsolation() throws SQLException { return delegate.getTransactionIsolation(); }
    @Override public SQLWarning getWarnings() throws SQLException { return delegate.getWarnings(); }
    @Override public void clearWarnings() throws SQLException { delegate.clearWarnings(); }
    @Override public Statement createStatement(int a, int b) throws SQLException { return delegate.createStatement(a, b); }
    @Override public Statement createStatement(int a, int b, int c) throws SQLException { return delegate.createStatement(a, b, c); }
    @Override public Map<String, Class<?>> getTypeMap() throws SQLException { return delegate.getTypeMap(); }
    @Override public void setTypeMap(Map<String, Class<?>> m) throws SQLException { delegate.setTypeMap(m); }
    @Override public void setHoldability(int h) throws SQLException { delegate.setHoldability(h); }
    @Override public int getHoldability() throws SQLException { return delegate.getHoldability(); }
    @Override public Savepoint setSavepoint() throws SQLException { return delegate.setSavepoint(); }
    @Override public Savepoint setSavepoint(String s) throws SQLException { return delegate.setSavepoint(s); }
    @Override public void rollback(Savepoint sp) throws SQLException { delegate.rollback(sp); }
    @Override public void releaseSavepoint(Savepoint sp) throws SQLException { delegate.releaseSavepoint(sp); }
    @Override public Clob createClob() throws SQLException { return delegate.createClob(); }
    @Override public Blob createBlob() throws SQLException { return delegate.createBlob(); }
    @Override public NClob createNClob() throws SQLException { return delegate.createNClob(); }
    @Override public SQLXML createSQLXML() throws SQLException { return delegate.createSQLXML(); }
    @Override public void setClientInfo(String k, String v) throws SQLClientInfoException { delegate.setClientInfo(k, v); }
    @Override public void setClientInfo(Properties p) throws SQLClientInfoException { delegate.setClientInfo(p); }
    @Override public String getClientInfo(String k) throws SQLException { return delegate.getClientInfo(k); }
    @Override public Properties getClientInfo() throws SQLException { return delegate.getClientInfo(); }
    @Override public Array createArrayOf(String t, Object[] e) throws SQLException { return delegate.createArrayOf(t, e); }
    @Override public Struct createStruct(String t, Object[] a) throws SQLException { return delegate.createStruct(t, a); }
    @Override public void setSchema(String s) throws SQLException { delegate.setSchema(s); }
    @Override public String getSchema() throws SQLException { return delegate.getSchema(); }
    @Override public void abort(Executor e) throws SQLException { delegate.abort(e); }
    @Override public void setNetworkTimeout(Executor e, int ms) throws SQLException { delegate.setNetworkTimeout(e, ms); }
    @Override public int getNetworkTimeout() throws SQLException { return delegate.getNetworkTimeout(); }
}
