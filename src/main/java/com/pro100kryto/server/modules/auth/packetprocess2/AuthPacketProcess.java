package com.pro100kryto.server.modules.auth.packetprocess2;


import com.pro100kryto.server.logger.ILogger;
import com.pro100kryto.server.modules.auth.connection.Connection;
import com.pro100kryto.server.modules.auth.connection.ConnectionRoles;
import com.pro100kryto.server.modules.auth.packet.PacketCreator;
import com.pro100kryto.server.utils.datagram.ConnectionInfo;
import com.pro100kryto.server.utils.datagram.packets.*;


public class AuthPacketProcess extends PacketProcess{
    private final String authorizableIP;
    private final int authorizablePort;
    
    public AuthPacketProcess(IPacketProcessCallback callback, ILogger logger,
                             String authorizableIP, int authorizablePort) {
        super(callback, logger);
        this.authorizableIP = authorizableIP;
        this.authorizablePort = authorizablePort;
    }

    
    @Override
    public void processPacket(IPacket packet) throws Throwable {
        DataReader reader = packet.getDataReader();
        String nickname = reader.readByteString(); // 1 byte define length + UTF_8
        String passHash = reader.readIntString();
        String clientVer = reader.readByteString();

        // prepare data
        IPacketInProcess newPacket = callback.getPacketPool().getNextPacket();

        try{
            DataCreator creator = newPacket.getDataCreator();
            newPacket.setEndPoint(new EndPoint(packet.getEndPoint()));

            try{
                if (checkClient(clientVer)) {
                    UserDB userDB = checkUser(nickname, passHash);
                    if (userDB.connInfo == ConnectionInfo.OK) {
                        Connection conn = callback.getIAuth().authorize(
                                userDB.userId,
                                userDB.nickname,
                                ConnectionRoles.USER,
                                packet.getEndPoint());

                        PacketCreator.authSucc(creator,
                                authorizableIP, authorizablePort,
                                conn.getKeyCrypt(),
                                conn.getConnPass(),
                                conn.getConnId());
                    } else {
                        PacketCreator.authFail(creator, userDB.connInfo);
                    }
                } else {
                    PacketCreator.authFail(creator, ConnectionInfo.WRONG_CLIENT);
                }

                callback.getSender().sendPacketAndRecycle(newPacket);

            } catch (Throwable throwable){
                newPacket.recycle();
                throw new Throwable(throwable);
            }

        } catch (NullPointerException ignored){
            // Packet/PacketPoolConnection/SenderConnection is null
        }
    }

    private UserDB checkUser(String nickname, String passHash){
        try {
            Iterable<com.pro100kryto.server.services.usersdatabase.connection.UserDB> usersDB
                    = callback.getDatabase().getAllUsers("nickname", nickname);

            if (!usersDB.iterator().hasNext()){
                logger.writeInfo("Wrong username");
                return USER_DB_NOT_FOUND;
            }

            while (usersDB.iterator().hasNext()){
                com.pro100kryto.server.services.usersdatabase.connection.UserDB userDB
                        = usersDB.iterator().next();
                if (!passHash.equals(userDB.getValue("password"))) continue;
                return new UserDB(
                        ConnectionInfo.OK,
                        userDB.getUserId(),
                        userDB.getValue("nickname").toString());
            }

            logger.writeInfo("Wrong password");
            return USER_DB_NOT_FOUND;
        } catch (Exception ex){
            logger.writeError("Failed check user: "+ex.getMessage());
        }
        return USER_DB_ERROR;
    }

    private boolean checkClient(String version){
        return true;
    }

    private static final class UserDB{
        public final byte connInfo;
        public final long userId;
        public final String nickname;

        public UserDB(byte connInfo, long userId, String nickname) {
            this.connInfo = connInfo;
            this.userId = userId;
            this.nickname = nickname;
        }
    }

    private static final UserDB USER_DB_NOT_FOUND = new UserDB(ConnectionInfo.WRONG_USER, 0, null);
    private static final UserDB USER_DB_ERROR = new UserDB(ConnectionInfo.DB_ERROR, 0, null);
}
