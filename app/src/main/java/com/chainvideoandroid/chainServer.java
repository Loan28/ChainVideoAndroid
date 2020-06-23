package com.chainvideoandroid;
import android.content.Intent;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.netty.NettyServerBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class chainServer {

    private static final Logger logger = Logger.getLogger(chainServer.class.getName());
    private  final int port;
    private final Server server;


    public chainServer(int port, ImageStore imageStore) {
        this(NettyServerBuilder.forPort(port), port, imageStore);
    }

    public chainServer(ServerBuilder serverBuilder, int port, ImageStore imageStore){
        this.port = port;
        chainMLService Services = new chainMLService(imageStore);
        server = serverBuilder.addService(Services).build();
    }

    public void start() throws IOException{
        server.start();
        logger.info("server started on port" + port);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.err.println("Shut down gRPC server because JVM shuts down");
                try {
                    chainServer.this.stop();
                } catch (InterruptedException e) {
                    e.printStackTrace(System.err);
                }
                System.err.println("server shut down");
            }
        });
    }
    public void stop() throws  InterruptedException {
        if (server != null) {
            server.shutdownNow().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    void  blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }


    public static void main(String args) throws InterruptedException, IOException {
        DiskImageStore imageStore = new DiskImageStore("img");
        chainServer server = new chainServer(50051, imageStore);
        server.start();

        //server.blockUntilShutdown();
    }

}
