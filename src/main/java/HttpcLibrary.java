import java.net.URI;
import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.*;
import static java.nio.channels.SelectionKey.OP_READ;

public class HttpcLibrary {

    private static final Logger logger = LoggerFactory.getLogger(HttpcLibrary.class);
    private long sequenceNumberValue = 1L;

    public String helpRequest(String []args) {
        if (args.length == 1) {
            return ("\nhttpc is a curl-like application but supports HTTP protocol only.\n\n" +
                    "Usage:\n" +
                    "    httpc command [arguments]\n" +
                    "The commands are:\n" +
                    "    get     executes a HTTP GET request and prints the response.\n" +
                    "    post    executes a HTTP POST request and prints the response.\n" +
                    "    help    prints this screen.\n\n" +
                    "Use \"httpc help [command]\" for more information about a command.\n");
        } else if(args.length == 2) {
            if (args[1].equalsIgnoreCase("get")) {
                return ("\nusage: httpc get [-v] [-h key:value] URL\n\n" +
                        "Get executes a HTTP GET request for a given URL.\n\n" +
                        "   -v             Prints the detail of the response such as protocol, status, and headers.\n" +
                        "   -h key:value   Associates headers to HTTP Request with the format 'key:value'.\n");
            } else if (args[1].equalsIgnoreCase("post")) {
                return ("\nusage: httpc post [-v] [-h key:value] [-d inline-data] [-f file] URL\n\n" +
                        "Post executes a HTTP POST request for a given URL with inline data or from file.\n\n" +
                        "   -v             Prints the detail of the response such as protocol, status, and headers.\n" +
                        "   -h key:value   Associates headers to HTTP Request with the format 'key:value'.\n" +
                        "   -d             Asociates an inline data to the body HTTP POST request.\n" +
                        "   -f             Associates the content of a file to the body HTTP POST request.\n");
            } else {
                System.exit(0);
                return "";
            }
        } else {
            System.exit(0);
            return "";
        }
    }

    public String sendRequest(String method, String URL, String file, String data, Map headers, Boolean verbose, Boolean redirect, SocketAddress routerAddress, InetSocketAddress serverAddress){
        URI uri;
        String dataToAppend = data;
        String request = "";
        String responseHeader = "";
        String responseBody = "";
        String host = "";
        String rawPath = "";
        String query = "";
        int port;
        int status;

        try {
            uri =  new URI(URL);

        }catch (Exception e){
            System.out.println("Error retrieving uri");
            return "";
        }

        host = uri.getHost(); //httpbin.org
        rawPath = uri.getRawPath(); // /post or /get
        query = uri.getQuery();
        port = uri.getPort(); //-1


        if(rawPath == null || rawPath.isEmpty()) {
            rawPath = "/";
        }

        if(query == null || query.isEmpty()) {
            query = "";
        }

        if(port == -1) {
            port = 80;
        }

        if (method.equalsIgnoreCase("GET")) {
            request = ("GET " + rawPath + "?" + query + " HTTP/1.0\r\nHost: " + host + "\r\n");

            if(!headers.isEmpty()) {
                request = addHeaders(request, headers);
            }

            request += "\r\n";

        } else if(method.equalsIgnoreCase("POST")) {

            if(!file.isEmpty()){
                dataToAppend = getFileData(file);
            }

            request = ("POST " + rawPath + " HTTP/1.0\r\nHost: " + host + "\r\nContent-Length: " + dataToAppend.length() + "\r\n" + "User-Agent: Mozilla/5.0\r\n");

            if(!headers.isEmpty()) {
                request = addHeaders(request, headers);
            }

            request += "\r\n" + dataToAppend;
        }

        try{
            /* Setup 3-way handshaking
                0: Data
                1: ACK
                2: SYN
                3: SYN-ACK
                4: NAK
            */
            Packet response1 = threeWayHandshake(routerAddress, serverAddress);

            System.out.println("olla");

            sequenceNumberValue +=1L;
            Packet response = runClient(routerAddress, serverAddress, request, 0, sequenceNumberValue);

            // ByteBuffer byteBufferRead = ByteBuffer.allocate(1024*1024);

            // String temp[] = utf8.decode(response).toString().split("\\r\\n\\r\\n");
            String temp[] = response.getPayload().toString().split("\\r\\n\\r\\n");

            responseHeader = temp[0];
            responseBody = temp[1];

        } catch (Exception e) {
            System.out.println("Error retrieving response");

        }

        status = getStatus(responseHeader);

        //301, 302, 304
        if(status >= 300 && status <= 304 && redirect){

            String redirectLocation = getRedirect(responseHeader);

            //304 Not Modified
            if(status == 304) {
                System.out.println("File has not changed.");
                return "";
            } else {
                return sendRequest(method, redirectLocation, file, data, headers, verbose, redirect, routerAddress, serverAddress);
            }
        }

        //check -v header and body or just body
        if(verbose == true && !(responseHeader.isEmpty())){
            return(responseHeader.trim() + "\r\n\r\n" + responseBody.trim());
        } else {
            return responseBody.trim();
        }
    }


    public int getStatus(String response){
        int status = -1;
        String pattern = "HTTP/\\d\\.\\d (\\d{3}) (.*)";
        String responseByLines[] = response.split("\r\n");

        for(String value: responseByLines ) {
            if(value.matches(pattern)) {
                status = Integer.parseInt(value.substring(value.indexOf("HTTP/") + 9, value.indexOf("HTTP/") + 12));
                break;
            }
        }

        return status;
    }

    public String getRedirect(String response){

        String responseByLines[] = response.split("\r\n");
        String redirectLocation = "";
        String pattern = "Location: (.*)";

        for(String value: responseByLines ){
            if(value.matches(pattern)) {
                redirectLocation = value.substring(value.indexOf("Location: ") + 9);
                break;
            }
        }

        return redirectLocation.trim();
    }


    public String addHeaders(String requestString, Map<String, String> keyValue) {

        for (Map.Entry<String,String> pair : keyValue.entrySet()){
            requestString = (requestString + pair.getKey() + ": " +pair.getValue() + "\r\n");
        }

        return requestString;
    }

    public String getFileData(String fileUri) {

        BufferedReader bufferedReader = null;

        try {
            bufferedReader = new BufferedReader(new FileReader(fileUri));
            StringBuilder stringBuilder = new StringBuilder();
            String fileData = bufferedReader.readLine();

            while (fileData != null) {
                stringBuilder.append(fileData);
                fileData = bufferedReader.readLine();
            }

            bufferedReader.close();

            return stringBuilder.toString();

        } catch (Exception e) {
            try {
                bufferedReader.close();
            } catch (Exception er) {
                System.out.println("Error getting file data");
            }
        }

        return "";
    }

    public Packet threeWayHandshake(SocketAddress routerAddr, InetSocketAddress serverAddr) throws IOException {

        Packet response = null;

        try(DatagramChannel channel = DatagramChannel.open()) {
            //build packet object
            Packet packet = new Packet.Builder()
                    .setType(2)
                    .setSequenceNumber(sequenceNumberValue)
                    .setPortNumber(serverAddr.getPort())
                    .setPeerAddress(serverAddr.getAddress())
                    .setPayload(("").getBytes())
                    .create();

            channel.send(packet.toBuffer(), routerAddr);

            logger.info("Sending \"{}\" to router at {}", "", routerAddr);

            // Try to receive a packet within timeout.
            channel.configureBlocking(false); //non-blocking mode

            //multiplexor
            Selector selector = Selector.open();

            //Registers this channel with the given selector and perform blocking selection operation
            channel.register(selector, OP_READ);
            logger.info("Waiting for the response");
            selector.select(10000);

            //set the selected keys
            Set<SelectionKey> keys = selector.selectedKeys();

            if(keys.isEmpty()){
                logger.error("No response after timeout threeway");
                // Timer timer = new Timer();
                // timer.schedule( new threeWayHandshake(routerAddr, serverAddr, request, type, sequenceNumber), 1000);
                threeWayHandshake(routerAddr, serverAddr);
                // return response;
            }

            // only want single response.
            ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
            SocketAddress router = channel.receive(buf);
            buf.flip();
            Packet packetResp = Packet.fromBuffer(buf);
            logger.info("Packet: {}", packetResp);
            response = packetResp;
            logger.info("Router: {}", router);
            String payload = new String(packetResp.getPayload(), StandardCharsets.UTF_8);
            logger.info("Payload: {}",  payload);

            keys.clear();

            if(response.getType() == 3){
                System.out.println(response.getType());

                Packet packet2 = new Packet.Builder()
                        .setType(1)
                        .setPortNumber(serverAddr.getPort())
                        .setPeerAddress(serverAddr.getAddress())
                        .setPayload(("").getBytes())
                        .create();
                channel.send(packet2.toBuffer(), routerAddr);
                logger.info("Sending \"{}\" to router at {}", "", routerAddr);
            } else{
                logger.info("Received incorrect packet type {}", response.getType());
                threeWayHandshake(routerAddr, serverAddr);
            }
        }

        return response;
    }

    public Packet runClient(SocketAddress routerAddr, InetSocketAddress serverAddr, String request, int type, long sequenceNumber) throws IOException {
        System.out.println("runClient");

        Packet response = null;

        try(DatagramChannel channel = DatagramChannel.open()){
            Packet packet = new Packet.Builder()
                    .setType(type)
                    .setSequenceNumber(sequenceNumber)
                    .setPortNumber(serverAddr.getPort())
                    .setPeerAddress(serverAddr.getAddress())
                    .setPayload(request.getBytes())
                    .create();
            channel.send(packet.toBuffer(), routerAddr);

            logger.info("Sending \"{}\" to router at {}", request, routerAddr);

            // Try to receive a packet within timeout.
            channel.configureBlocking(false); //non-blocking mode

            //multiplexor
            Selector selector = Selector.open();

            //Registers this channel with the given selector and perform blocking selection operation
            channel.register(selector, OP_READ);
            logger.info("Waiting for the response");
            selector.select(5000);

            Set<SelectionKey> keys = selector.selectedKeys();

            if(keys.isEmpty()){
                logger.error("No response after timeout run client");
                // Timer timer = new Timer();
                // timer.schedule( new resendPacket(routerAddr, serverAddr, request, type, sequenceNumber), 1000);
                runClient(routerAddr, serverAddr, request, type, sequenceNumber);
                // return response;
            }

            // only want single response.
            ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
            SocketAddress router = channel.receive(buf);
            buf.flip();
            Packet packetResp = Packet.fromBuffer(buf);
            logger.info("Packet: {}", packetResp);
            response = packetResp;
            logger.info("Router: {}", router);
            String payload = new String(packetResp.getPayload(), StandardCharsets.UTF_8);
            logger.info("Payload: {}",  payload);


            Packet packet2 = new Packet.Builder()
                    .setType(1)
                    .setSequenceNumber(sequenceNumber)
                    .setPortNumber(serverAddr.getPort())
                    .setPeerAddress(serverAddr.getAddress())
                    .setPayload(("").getBytes())
                    .create();
            channel.send(packet2.toBuffer(), routerAddr);

            keys.clear();
        }

        return response;
    }

}
