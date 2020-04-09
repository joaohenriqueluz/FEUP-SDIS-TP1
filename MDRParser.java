import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.net.MulticastSocket;
import java.security.NoSuchAlgorithmException;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Random;
import java.util.Arrays;

import java.lang.Throwable;

public class MDRParser implements Runnable {
    Peer peer;
    DatagramPacket packet;

    private static final int BACKUP_BUFFER_SIZE = 64512; // bytes
    private static final String CRLF = "\r\n"; // CRLF delimiter
    private static final int INITIAL_WAITING_TIME = 1000; // 1 second
    private static final int RANDOM_TIME = 400; // milliseconds
    private static final int MAX_ATTEMPTS = 5;

    public MDRParser(Peer peer, DatagramPacket packet) {
        this.peer = peer;
        this.packet = packet;
    }

    public void parseMessageMDR() {
        try {
            // <Version> CHUNK <SenderId> <FileId> <ChunkNo> <CRLF><CRLF><Body>
            String received = new String(packet.getData(), 0, packet.getLength());
            Random rand = new Random();
            String[] receivedMessage = received.split("[\\u0020]+", 7); // blank space UTF-8

            int bodyStartIndex = received.lastIndexOf(CRLF) + CRLF.length();
            byte[] chunkBody = new byte[packet.getLength() - bodyStartIndex];
            chunkBody = Arrays.copyOfRange(packet.getData(), bodyStartIndex, packet.getLength());

            String protocolVersion = receivedMessage[0];
            String command = receivedMessage[1];
            String senderID = receivedMessage[2];
            String fileID = receivedMessage[3];
            String chunkNumber = receivedMessage[4];
            String key = this.peer.makeKey(chunkNumber, fileID);

            if (senderID.equals(this.peer.getID())) {
                return;
            }

            if (command.equals("CHUNK")) {
                System.out.println("CHUNK");

                // Checks if the chunk belongs to a local file
                if (this.peer.getStoredRecord().getChunkInfo(key) == null) {
                    // Only stores a new key if the chunk wasn't
                    // already restored by any of the other peers
                    if (!this.peer.getRestoreRecord().isRestored(key)) {
                        this.peer.getRestoreRecord().insertKey(key);
                    }
                } else {
                    if (!this.peer.getRestoreRecord().isRestored(key)) {
                        this.peer.getRestoreRecord().insertKey(key);
                        Chunk chunk = new Chunk(Integer.parseInt(chunkNumber), fileID, chunkBody.length, 0);
                        chunk.setData(chunkBody);
                        this.peer.getRestoreRecord().insertChunk(chunk);
                    }
                }
            } else {
                System.out.println("CHUNK command not found!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        this.parseMessageMDR();
    }
}