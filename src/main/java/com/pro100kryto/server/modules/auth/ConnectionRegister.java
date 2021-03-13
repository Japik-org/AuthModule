package com.pro100kryto.server.modules.auth;

import com.pro100kryto.server.modules.auth.connection.Connection;
import com.pro100kryto.server.utils.ServerUtils.IntCounterLocked;
import org.eclipse.collections.api.block.procedure.primitive.IntProcedure;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;
import org.jetbrains.annotations.Nullable;

import java.security.InvalidKeyException;


public final class ConnectionRegister {
    private final IntCounterLocked counter = new IntCounterLocked();
    private final LongObjectHashMap<IntArrayList> userIdConnectionsMap;
    private final IntObjectHashMap<Connection> connIdConnectionMap;

    public ConnectionRegister(int capacity) {
        userIdConnectionsMap = new LongObjectHashMap<>(capacity);
        connIdConnectionMap = new IntObjectHashMap<>(capacity);
    }

    public int count(long userId){
        try{
            return userIdConnectionsMap.get(userId).size();
        } catch (NullPointerException ignored){}
        return 0;
    }

    public boolean containsByUserId(long userId){
        return userIdConnectionsMap.containsKey(userId);
    }

    public boolean containsByConnId(int connId){
        return connIdConnectionMap.containsKey(connId);
    }

    public void set(Connection newConnection){
        connIdConnectionMap.put(newConnection.getConnId(), newConnection);

        if (userIdConnectionsMap.containsKey(newConnection.getUserId())){
            userIdConnectionsMap.get(newConnection.getUserId()).add(newConnection.getConnId());
        } else {
            IntArrayList list = new IntArrayList();
            list.add(newConnection.getConnId());
            userIdConnectionsMap.put(newConnection.getUserId(), list);
        }
    }

    @Nullable
    public Connection get(int connId){
        return connIdConnectionMap.get(connId);
    }

    @Nullable
    public Connection getFirst(){
        return connIdConnectionMap.getFirst();
    }

    public void removeAllByUserId(long userId){
        userIdConnectionsMap.remove(userId).each(
                (IntProcedure) connIdConnectionMap::remove);
    }

    public void removeByConnId(int connId){
        Connection conn = connIdConnectionMap.remove(connId);
        userIdConnectionsMap.get(conn.getUserId()).remove(connId);
    }

    public long getUserId(int connId) throws InvalidKeyException{
        try {
            return connIdConnectionMap.get(connId).getUserId();
        } catch (NullPointerException ignored){}
        throw new InvalidKeyException();
    }

    public ImmutableIntList getConnId(long userId) throws InvalidKeyException{
        try {
            return userIdConnectionsMap.get(userId).toImmutable();
        } catch (NullPointerException ignored){}

        throw new InvalidKeyException();
    }

    public void clearSafe(){
        while (connIdConnectionMap.notEmpty()){
            try {
                Connection conn = connIdConnectionMap.getFirst();
                connIdConnectionMap.remove(conn.getConnId());
                userIdConnectionsMap.remove(conn.getUserId());
            } catch (Throwable ignored){}
        }
    }

    public void clearFast(){
        connIdConnectionMap.clear();
        userIdConnectionsMap.clear();
    }

    public boolean isEmpty(){
        return connIdConnectionMap.isEmpty() && userIdConnectionsMap.isEmpty();
    }

    public int getNextConnId(){
        return counter.incrementAndGet();
    }
}