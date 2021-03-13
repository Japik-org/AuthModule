package com.pro100kryto.server.modules.auth.packet;

public class PacketId {
    public static final class Server{
        public static final short WRONG = 0;
        public static final short AUTH = 1;
        public static final short PONG = 2;
        public static final short DISCONNECT = 3;
        public static final short MSG_ERROR = 4;
    }

    public static final class Client{
        public static final short WRONG = 0;
        public static final short AUTH = 1;
        public static final short PING = 2;
        public static final short DISCONNECT = 3;
        public static final short VERSION = 4;
    }
}
