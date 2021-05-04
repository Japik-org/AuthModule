package com.pro100kryto.server.modules.auth.packetprocess2;

import com.pro100kryto.server.logger.ILogger;
import com.pro100kryto.server.modules.auth.MsgErrorCode;
import com.pro100kryto.server.modules.auth.packet.PacketCreator;
import com.pro100kryto.server.utils.datagram.packetprocess2.IPacketProcess;
import com.pro100kryto.server.utils.datagram.packets.EndPoint;
import com.pro100kryto.server.utils.datagram.packets.IPacket;
import com.pro100kryto.server.utils.datagram.packets.IPacketInProcess;

import java.util.Objects;

public abstract class PacketProcess implements IPacketProcess {
    protected final IPacketProcessCallback callback;
    protected final ILogger logger;

    protected PacketProcess(IPacketProcessCallback callback, ILogger logger) {
        this.callback = callback;
        this.logger = logger;
    }

    @Override
    public final void run(IPacket packet) {
        try {
            processPacket(packet);
        } catch (Throwable throwable) {
            try {
                IPacketInProcess newPacket = callback.getPacketPool().getNextPacket();
                Objects.requireNonNull(newPacket);
                try {
                    PacketCreator.msgError(newPacket.getDataCreator(), MsgErrorCode.WrongPacket);
                    newPacket.setEndPoint(new EndPoint(packet.getEndPoint()));
                    callback.getSender().sendPacketAndRecycle(newPacket);
                    return;
                } catch (Throwable ignored){
                }
                newPacket.recycle();
                logger.writeException(throwable);
            } catch (NullPointerException ignored){
            }
        }
    }

    public abstract void processPacket(IPacket packet) throws Throwable;
}
