package com.pro100kryto.server.modules.auth;

import com.pro100kryto.server.modules.auth.packet.PacketHeaderInfo;
import com.pro100kryto.server.utils.datagram.objectpool.ObjectPool;
import com.pro100kryto.server.utils.datagram.packetprocess2.IPacketProcess;
import com.pro100kryto.server.utils.datagram.packetprocess2.ProcessPacketInThreadPool;
import com.pro100kryto.server.utils.datagram.packets.DataReader;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;

public class AuthProcess extends ProcessPacketInThreadPool<AuthProcess> {

    public AuthProcess(ObjectPool<AuthProcess> processPool, IntObjectHashMap<IPacketProcess> packetIdPacketProcessMap) {
        super(processPool, true, packetIdPacketProcessMap);
    }


    @Override
    protected void poolPutThis() {
        processPool.put(this);
    }

    @Override
    protected boolean poolContainsThis() {
        return processPool.contains(this);
    }

    @Override
    protected void poolRemoveThis() {
        processPool.remove(this);
    }

    // -------------

    @Override
    protected IPacketProcess getPacketProcess() {
        try {
            DataReader reader = packet.getDataReader();
            reader.setPosition(PacketHeaderInfo.Client.POS_PACKET_ID);
            int packetId = reader.readShort();
            IPacketProcess packetProcess = packetIdPacketProcessMap.get(packetId);
            reader.setPosition(PacketHeaderInfo.Client.POS_BODY);
            return packetProcess;
        } catch (Throwable ignored){
        }
        return null;
    }
}
