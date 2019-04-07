import java.io.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.net.SocketAddress;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.util.*;
import static java.nio.charset.StandardCharsets.UTF_8;

public class HttpfsLibrary {

    private static final Logger logger = LoggerFactory.getLogger(HttpfsLibrary.class);
    private long sequenceNumberValue = 1L;
    // private Timer timer = new Timer();

    public void listenAndServe(int port, String dir) throws IOException {
        try (DatagramChannel channel = DatagramChannel.open()) {
            channel.bind(new InetSocketAddress(port));
            logger.info("HttpfsLibrary is listening at {}", channel.getLocalAddress());
            logger.info("New client from {}", channel.getRemoteAddress());
            threeWayHandshake(channel, dir);
            // readRequestAndRepeat(channel, dir);
        } catch (Exception e) {
            logger.error("Echo error {}", e);
        }
    }

    public void readRequestAndRepeat(DatagramChannel socket, String dir) {

        try (DatagramChannel client = socket) {
            ByteBuffer buf = ByteBuffer
                    .allocate(Packet.MAX_LEN)
                    .order(ByteOrder.BIG_ENDIAN);

            for (; ; ) {
                SocketAddress router;
                Packet packet;

                /* Setup 3-way handshaking
                    0: Data
                    1: ACK
                    2: SYN
                    3: SYN-ACK
                    4: NAK
                */

                buf.clear();
                router = client.receive(buf);
                buf.flip();
                packet = Packet.fromBuffer(buf);
                buf.flip();

                System.out.println("olla" + packet.getType());
                if(packet.getSequenceNumber() == (sequenceNumberValue+1L)){
                    sequenceNumberValue+=1;

                    String payload = new String(packet.getPayload(), UTF_8);
                    logger.info("Packet: {}", packet);
                    logger.info("Payload: {}", payload);
                    logger.info("Router: {}", router);

                    File folder = new File(dir);
                    String response = display(payload, folder);

                    selectiveRepeat(socket, dir, packet, router, response);
                }

            }

        } catch (IOException e) {
            logger.error("Echo error {}", e);
        }
    }

    public String display(String x, File folder){

        String listFiles = "";
        String listOfFiles = "";
        String output = "";
        String fileName = "";
        String requestIgnoreQuery = "";
        String input = x;
        //input: GET /FileName? HTTP/1.0
        //Host: localhost...

        String inputMessage[] = input.split("\r\n\r\n");
        String header = (inputMessage.length>0? inputMessage[0] : "");
        String body = "";

        if(inputMessage.length > 1) {
            body = inputMessage[1];
        }

        //request[1] = /FileName?
        String request[] = header.split("\r\n")[0].split(" ");
        String method = (request.length>0? request[0] : "");

        System.out.println(method +"2");

        if(method.equalsIgnoreCase("get")){

            //requestIgnoreQuery = /FileName
            requestIgnoreQuery = request[1].substring(0, request[1].lastIndexOf("?"));

            //No file name specified, so list all files
            if(requestIgnoreQuery.equals("/")){

                listOfFiles = getFiles(folder, listFiles);
                output = "HTTP/1.0 200 OK\r\n\n" + listOfFiles;

                //Filename specified so get content
            } else if(requestIgnoreQuery.substring(0,1).equals("/")) {

                fileName = requestIgnoreQuery.substring(1);

                //for security
                if(fileName.contains("../") || fileName.contains("..\\") || fileName.contains("httpfs") || fileName.contains("httpfsLibrary")){
                    output = "HTTP/1.0 403 Error\r\n\n" + "Access to the file path is not allowed.";
                    return output;
                }

                String content = getContent(folder, fileName);

                if(!content.isEmpty()) {

                    output = "HTTP/1.0 200 OK" + "\r\n\n" + content + "\r\n\n";

                } else{
                    output = "HTTP/1.0 404 Error Not Found\r\n\n" + "File was not found.";
                }
            }

        } else if(method.equalsIgnoreCase("post")){

            // /filename
            requestIgnoreQuery = request[1];

            if(requestIgnoreQuery.equals("/")){

                output = "HTTP/1.0 403 Error Not Allowed\r\n\n";

            } else if(requestIgnoreQuery.substring(0,1).equals("/")) {

                fileName = requestIgnoreQuery.substring(1);

                //for security
                if(fileName.contains("../") || fileName.contains("..\\") || fileName.contains("httpfs") || fileName.contains("httpfsLibrary")){
                    output = "HTTP/1.0 403 Error\r\n\n" + "Access to the file path is not allowed.";
                    return output;
                }

                Boolean fileWritten = false;
                FileWriter fileWriter = null;
                String folderPath = folder.getPath().replace("\\", "\\\\");

                try{
                    fileWriter = new FileWriter(folderPath + "\\" + fileName, false);
                    fileWriter.write(body);
                    fileWritten = true;
                } catch (IOException e){

                } finally{
                    try{
                        fileWriter.close();
                    }catch (IOException e) {

                    }
                }

                if(fileWritten){
                    output = "HTTP/1.0 200 OK\r\n\n" + "File has been written to.";
                } else{
                    output = "HTTP/1.0 405 Error Writing to file\r\n\n" + "Writing to file failed.";
                }
            }

        } else{
            output = "HTTP/1.0 404 Error\r\n" + "Invalid command";
        }

        return output;
    }

    public String getFiles(File folder, String listOfFiles){
        File[] fileNames = folder.listFiles();

        for(File file : fileNames) {

            if(file.isDirectory()) {
                listOfFiles += (file.getPath() + "\n");
                listOfFiles += getFiles(file, "");

            } else {
                listOfFiles += (file.getPath() + "\n");
            }
        }
        return listOfFiles;
    }

    public String getContent(File folder, String fileName) {

        File[] fileNames = folder.listFiles();
        String fileNameNoExtension = "";
        String content = "";

        for (File file : fileNames) {

            if(file.isDirectory()){
                getContent(file, fileName);
            } else {
                fileNameNoExtension = file.getName();

                if (fileNameNoExtension.indexOf(".") > 0) {
                    fileNameNoExtension = fileNameNoExtension.substring(0, fileNameNoExtension.indexOf('.'));
                }

                if (fileNameNoExtension.equalsIgnoreCase(fileName)) {

                    try {
                        BufferedReader br = new BufferedReader(new FileReader(file));
                        String strLine;
                        content += ("File: " + file.getName() + "\n");
                        while ((strLine = br.readLine()) != null) {
                            content += (strLine + "\n");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        return content;
    }

    public void threeWayHandshake(DatagramChannel socket, String dir) {
        System.out.println("HERERERER");

        try (DatagramChannel client = socket) {
            ByteBuffer buf = ByteBuffer
                    .allocate(Packet.MAX_LEN)
                    .order(ByteOrder.BIG_ENDIAN);

            for (; ; ) {
                SocketAddress router;
                Packet packet;

                /* Setup 3-way handshaking
                    0: Data
                    1: ACK
                    2: SYN
                    3: SYN-ACK
                    4: NAK
                */

                while(true){
                    buf.clear();
                    router = client.receive(buf);
                    buf.flip();
                    packet = Packet.fromBuffer(buf);
                    buf.flip();
                    if(packet.getType() == 2){
                        sequenceNumberValue = packet.getSequenceNumber();
                        Packet resp = packet.toBuilder()
                                .setType(3)
                                .setPayload(("").getBytes())
                                .create();
                        client.send(resp.toBuffer(), router);
                        buf.clear();
                        break;
                    } else {
                        logger.info("Received incorrect packet type {}", packet.getType());
                        return;
                    }
                }

                Timer timer = new Timer();
                timer.schedule( new threeWay(socket, dir), 1000);

                while(true){
                    buf.clear();
                    client.receive(buf);
                    buf.flip();
                    packet = Packet.fromBuffer(buf);
                    buf.flip();
                    if(packet.getType() == 1){
                        timer.cancel();
                        buf.clear();
                        break;
                    }
                }
                readRequestAndRepeat(socket, dir);
                // ForkJoinPool.commonPool().submit(() -> readRequestAndRepeat(socket, dir));
            }

        } catch (IOException e) {
            logger.error("Echo error {}", e);
        }
    }

    public void selectiveRepeat(DatagramChannel socket, String dir, Packet pack, SocketAddress route, String respon) {
        System.out.println("selectiveRepeat");

        try (DatagramChannel client = socket) {
            ByteBuffer buf = ByteBuffer
                    .allocate(Packet.MAX_LEN)
                    .order(ByteOrder.BIG_ENDIAN);

            for (; ; ) {
                SocketAddress router = route;
                Packet packet = pack;

                /* Setup 3-way handshaking
                    0: Data
                    1: ACK
                    2: SYN
                    3: SYN-ACK
                    4: NAK
                */

                String response = respon;
                Charset utf8 = StandardCharsets.UTF_8;
                ByteBuffer byteBufferWrite = utf8.encode(response);
                Packet resp = packet.toBuilder()
                        .setType(0)
                        .setSequenceNumber(sequenceNumberValue)
                        .setPayload(byteBufferWrite.array())
                        .create();
                client.send(resp.toBuffer(), router);
                buf.clear();

                Timer timer = new Timer();
                timer.schedule( new resendPacket(socket, dir, packet, router, response), 1000);

                while(true){
                    System.out.println("selectiveRepeat 1");
                    buf.clear();
                    client.receive(buf);
                    buf.flip();
                    packet = Packet.fromBuffer(buf);
                    buf.flip();
                    System.out.println(packet.getType() + " : " + (packet.getSequenceNumber() == sequenceNumberValue));
                    if(packet.getType() == 1 && packet.getSequenceNumber() == sequenceNumberValue){
                        System.out.println("true");
                        buf.clear();
                        timer.cancel();
                        break;
                    }
                }
                threeWayHandshake(socket, dir);
                // ForkJoinPool.commonPool().submit(() -> readRequestAndRepeat(socket, dir));

            }

        } catch (IOException e) {
            logger.error("Echo error {}", e);
        }
    }

    class resendPacket extends TimerTask {

        private DatagramChannel socket;
        private String dir;
        private String response;
        private Packet packet;
        private SocketAddress router;

        resendPacket(DatagramChannel socket, String dir, Packet packet, SocketAddress router, String response){
            this.socket = socket;
            this.dir = dir;
            this.packet = packet;
            this.router = router;
            this.response = response;
        }

        public void run() {
            System.out.println("Time's up!");
            selectiveRepeat(socket, dir, packet, router, response);
        }
    }

    class threeWay extends TimerTask {

        private DatagramChannel socket;
        private String dir;

        threeWay(DatagramChannel socket, String dir){
            this.socket = socket;
            this.dir = dir;
        }

        public void run() {
            System.out.println("Time's up 3!");
            threeWayHandshake(socket, dir);
        }
    }

    public Packet createPacket(ByteBuffer buf, int type, long sequenceNumber, String payload){

        Packet packet;
        Packet response = null;

        try{
            packet = Packet.fromBuffer(buf);
            response = packet.toBuilder()
                    .setType(type)
                    .setSequenceNumber(sequenceNumber)
                    .setPayload(payload.getBytes())
                    .create();
        }catch(Exception e){

        }
        return response;
    }

    class MyThread extends Thread {

        private DatagramChannel socket;
        private String dir;

        MyThread(DatagramChannel socket, String dir){
            this.socket = socket;
            this.dir = dir;
        }

        public void run() {
            threeWayHandshake(socket, dir);
        }
    }



}