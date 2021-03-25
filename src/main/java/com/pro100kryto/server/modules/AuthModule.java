package com.pro100kryto.server.modules;

import com.pro100kryto.server.StartStopStatus;
import com.pro100kryto.server.logger.ILogger;
import com.pro100kryto.server.module.AModuleConnection;
import com.pro100kryto.server.module.IModuleConnectionSafe;
import com.pro100kryto.server.module.Module;
import com.pro100kryto.server.modules.auth.AuthProcess;
import com.pro100kryto.server.modules.auth.ConnectionRegister;
import com.pro100kryto.server.modules.auth.connection.*;
import com.pro100kryto.server.modules.auth.packet.PacketId;
import com.pro100kryto.server.modules.auth.packetprocess2.AuthPacketProcess;
import com.pro100kryto.server.modules.auth.packetprocess2.IPacketProcessCallback;
import com.pro100kryto.server.modules.packetpool.connection.IPacketPoolModuleConnection;
import com.pro100kryto.server.modules.receiverbuffered.connection.IReceiverBufferedModuleConnection;
import com.pro100kryto.server.modules.sender.connection.ISenderModuleConnection;
import com.pro100kryto.server.service.IServiceConnectionSafe;
import com.pro100kryto.server.service.IServiceControl;
import com.pro100kryto.server.services.usersdatabase.connection.IUsersDatabaseServiceConnection;
import com.pro100kryto.server.utils.datagram.objectpool.ObjectPool;
import com.pro100kryto.server.utils.datagram.packetprocess2.IPacketProcess;
import com.pro100kryto.server.utils.datagram.packetprocess2.ProcessorThreadPool;
import com.pro100kryto.server.utils.datagram.packets.IEndPoint;
import com.pro100kryto.server.utils.datagram.packets.IPacket;
import org.eclipse.collections.api.block.procedure.Procedure;
import org.eclipse.collections.api.block.procedure.primitive.IntProcedure;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;

import java.security.InvalidKeyException;
import java.util.Objects;
import java.util.Random;

public class AuthModule extends Module
        implements IPacketProcessCallback {
    private IAuthModuleConnection iAuthModuleConnection;

    private IModuleConnectionSafe<IReceiverBufferedModuleConnection> receiverModuleConnection;
    private IModuleConnectionSafe<IPacketPoolModuleConnection> packetPoolModuleConnection;
    private IModuleConnectionSafe<ISenderModuleConnection> senderModuleConnection;
    private IServiceConnectionSafe<IUsersDatabaseServiceConnection> usersDbServiceConnection;

    private IntObjectHashMap<IModuleConnectionSafe<IAuthorizableModuleConnection>> authorizableModuleConnectionMap;
    private IntObjectHashMap<IServiceConnectionSafe<IAuthorizableServiceConnection>> authorizableServiceConnectionMap;
    private IntObjectHashMap<IAuthorizable> authorizables;

    private ProcessorThreadPool<AuthProcess> processor;
    private ObjectPool<AuthProcess> processesPool;
    private IntObjectHashMap<IPacketProcess> packetIdPacketProcessMap;

    private ConnectionRegister connectionRegister;

    private boolean multiConnDisabled = true;

    private static final Random random = new Random();


    public AuthModule(IServiceControl service, String name) {
        super(service, name);
    }

    @Override
    protected void startAction() throws Throwable {

        moduleConnection = iAuthModuleConnection = new AuthModuleConnection(logger, name, type);

        {
            authorizableModuleConnectionMap = new IntObjectHashMap<>();
            authorizableServiceConnectionMap = new IntObjectHashMap<>();
            authorizables = new IntObjectHashMap<>();
            int i = 0;

            // services locally
            {
                String[] authorizableServiceNames =
                        settings.getOrDefault("authorizable-service-names", "").split(",");
                for (String serviceName : authorizableServiceNames) {
                    if (serviceName.isEmpty()) continue;
                    final IServiceConnectionSafe<IAuthorizableServiceConnection> iServiceConnectionSafe = initServiceConnection(serviceName);
                    authorizableServiceConnectionMap.put(i, iServiceConnectionSafe);
                    authorizables.put(i, new IAuthorizable() {
                        final IServiceConnectionSafe<IAuthorizableServiceConnection> iServiceConnectionSafe_ = iServiceConnectionSafe;

                        @Override
                        public void authorize(Connection connection) {
                            iServiceConnectionSafe_.getServiceConnection().authorize(connection);
                        }

                        @Override
                        public boolean isAuthorized(int connId) {
                            return iServiceConnectionSafe_.getServiceConnection().isAuthorized(connId);
                        }

                        @Override
                        public void reject(int connId) {
                            iServiceConnectionSafe_.getServiceConnection().reject(connId);
                        }
                    });
                    i++;
                }
            }

            // modules locally
            {
                String[] authorizableModuleNames =
                        settings.getOrDefault("authorizable-module-names", "").split(",");
                for (String moduleName : authorizableModuleNames){
                    if (moduleName.isEmpty()) continue;
                    final IModuleConnectionSafe<IAuthorizableModuleConnection> iModuleConnectionSafe = initModuleConnection(moduleName);
                    authorizableModuleConnectionMap.put(i, iModuleConnectionSafe);
                    authorizables.put(i, new IAuthorizable() {
                        final IModuleConnectionSafe<IAuthorizableModuleConnection> iModuleConnectionSafe_ = iModuleConnectionSafe;

                        @Override
                        public void authorize(Connection connection) {
                            iModuleConnectionSafe_.getModuleConnection().authorize(connection);
                        }

                        @Override
                        public boolean isAuthorized(int connId) {
                            return iModuleConnectionSafe_.getModuleConnection().isAuthorized(connId);
                        }

                        @Override
                        public void reject(int connId) {
                            iModuleConnectionSafe_.getModuleConnection().reject(connId);
                        }
                    });
                    i++;
                }
            }

            // services remotely
            {
                // TODO: 2/17/2021
            }
        }

        packetPoolModuleConnection = initModuleConnection(settings.getOrDefault("packetpool-module-name", "packetPool"));
        senderModuleConnection = initModuleConnection(settings.getOrDefault("sender-module-name", "sender"));
        receiverModuleConnection = initModuleConnection(settings.getOrDefault("receiver-module-name", "receiver"));

        usersDbServiceConnection = initServiceConnection(settings.getOrDefault("usersDb-service-name", "usersDb"));

        // ---------

        if (connectionRegister!=null && !connectionRegister.isEmpty()){
            logger.writeError("ConnectionRegister is not empty. Cleaning.");
            connectionRegister.clearFast();
        }
        connectionRegister = new ConnectionRegister(
                Integer.parseInt(settings.getOrDefault("max-connections", "256"))
        );

        // --------------

        String authorizableReceiverIp = settings.getOrDefault("authorizable-receiver-ip", "localhost");
        int authorizableReceiverPort = Integer.parseInt(
                settings.getOrDefault("authorizable-receiver-port", "49302"));

        packetIdPacketProcessMap = new IntObjectHashMap<>();
        packetIdPacketProcessMap.put(PacketId.Client.AUTH, new AuthPacketProcess(this, logger, authorizableReceiverIp, authorizableReceiverPort));

        int maxProcesses = Integer.parseInt(settings.getOrDefault("max-processes", "256"));
        processesPool = new ObjectPool<AuthProcess>(maxProcesses) {
            @Override
            protected AuthProcess createRecycledObject() {
                return new AuthProcess(processesPool, packetIdPacketProcessMap);
            }
        };
        processesPool.refill();
        processor = new ProcessorThreadPool<>(maxProcesses);

        // ----------

        multiConnDisabled = settings.getOrDefault("multiconnection-disabled", "false").equals("true");
    }

    @Override
    protected void stopAction(boolean force) throws Throwable{
        try {
            ((IAuthModuleConnection)moduleConnection).rejectAll();
        } catch (Throwable throwable) {
            logger.writeException(throwable, "failed reject connections!");
        }

        connectionRegister.clearFast();
        authorizables.clear();
        authorizableModuleConnectionMap.clear();
        authorizableServiceConnectionMap.clear();
    }

    // "receive" Packet and deliver to IProtocol (step 1)
    @Override
    public void tick() throws Throwable {
        try {
            IPacket packet = receiverModuleConnection.getModuleConnection().getNextPacket();
            Objects.requireNonNull(packet);

            try {
                AuthProcess process = processesPool.nextAndGet();
                process.setPacket(packet);
                processor.startOrRecycle(process);

            } catch (Throwable throwable){
                logger.writeException(throwable, "Failed process packet");
            }
        } catch (NullPointerException ignored){
        }
    }

    // -------- callback


    @Override
    public IUsersDatabaseServiceConnection getDatabase() {
        return usersDbServiceConnection.getServiceConnection();
    }

    @Override
    public IPacketPoolModuleConnection getPacketPool() {
        return packetPoolModuleConnection.getModuleConnection();
    }

    @Override
    public ISenderModuleConnection getSender() {
        return senderModuleConnection.getModuleConnection();
    }

    @Override
    public ConnectionRegister getConnectionRegister() {
        return connectionRegister;
    }

    @Override
    public IAuthModuleConnection getIAuth() {
        return iAuthModuleConnection;
    }

    private final class AuthModuleConnection extends AModuleConnection implements IAuthModuleConnection{

        public AuthModuleConnection(ILogger logger, String moduleName, String moduleType) {
            super(logger, moduleName, moduleType);
        }

        @Override
        public boolean isAliveModule() {
            return getStatus() == StartStopStatus.STARTED;
        }

        @Override
        public Connection authorize(long userId, String nickname, int roles, IEndPoint endPoint) {
            int connId;
            final byte[] keyCrypt = new byte[32];
            random.nextBytes(keyCrypt);
            final int connPass = random.nextInt();

            if (multiConnDisabled && connectionRegister.containsByUserId(userId)) {
                // restore connection
                Connection connOld = connectionRegister.getFirst();
                connId = connOld.getConnId();
            } else {
                // create new connection
                connId = connectionRegister.getNextConnId();
            }

            final Connection connection = new Connection(userId, connId, nickname, keyCrypt,
                    connPass, new ConnectionRoles(roles),
                    endPoint);
            connectionRegister.set(connection);

            authorizables.forEachValue((Procedure<IAuthorizable>) each -> {
                try{
                    each.authorize(connection);
                } catch (Throwable throwable){
                    logger.writeException(throwable, "failed authorize some IAuthorizable");
                }
            });

            return connection;
        }

        @Override
        public void rejectByConnId(int connId) {
            connectionRegister.removeByConnId(connId);

            authorizables.forEachValue((Procedure<IAuthorizable>)
                    each -> each.reject(connId));
        }

        @Override
        public void rejectAllByUserId(long userId){
            try {
                connectionRegister.getConnId(userId).forEach((IntProcedure)
                        each -> authorizables.forEachValue((Procedure<IAuthorizable>)
                                each2 -> each2.reject(each)));
            } catch (InvalidKeyException ignored){
            }
        }

        @Override
        public void rejectAll() {
            while (!connectionRegister.isEmpty()){
                final int connId = connectionRegister.getFirst().getConnId();

                authorizables.forEachValue((Procedure<IAuthorizable>)
                        each -> each.reject(connId));
            }
        }

        @Override
        public Connection getConnection(int connId) {
            return connectionRegister.get(connId);
        }
    }
}
