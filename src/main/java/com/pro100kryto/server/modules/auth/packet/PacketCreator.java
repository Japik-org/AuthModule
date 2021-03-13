package com.pro100kryto.server.modules.auth.packet;

import com.pro100kryto.server.modules.auth.MsgErrorCode;
import com.pro100kryto.server.utils.datagram.ConnectionInfo;
import com.pro100kryto.server.utils.datagram.packets.DataCreator;

public class PacketCreator {
    protected static void setHeader(DataCreator creator, short packetId){
        creator.setPosition(PacketHeaderInfo.Server.POS_PACKET_ID);
        creator.write(packetId);
        creator.setPosition(PacketHeaderInfo.Server.POS_BODY);
    }

    public static void msgError(DataCreator creator, MsgErrorCode msgError){
        setHeader(creator, PacketId.Server.MSG_ERROR);
        creator.write(msgError.ordinal());
    }

    public static void authFail(DataCreator creator, byte connectionInfo){
        setHeader(creator, PacketId.Server.AUTH);
        creator.write(connectionInfo);
    }

    public static void authSucc(DataCreator creator, String authorizableIP, int authorizablePort,
                                byte[] keyCrypt, int connPass, int connId){
        setHeader(creator, PacketId.Server.AUTH);
        creator.write(ConnectionInfo.OK);

        creator.writeIntStrings(authorizableIP);
        creator.write(authorizablePort);

        creator.write(32);
        creator.write(keyCrypt);

        creator.write(connPass);
        creator.write(connId);
    }
}
