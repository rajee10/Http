import java.io.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.ForkJoinPool;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class HttpfsLibrary {

    private static final Logger logger = LoggerFactory.getLogger(HttpfsLibrary.class);

    public void listenAndServe(int port, String dir) throws IOException {

        try (ServerSocketChannel server = ServerSocketChannel.open()) {

            server.bind(new InetSocketAddress(port));
            logger.info("HttpfsLibrary is listening at {}", server.getLocalAddress());

            for (; ; ) {
                SocketChannel client = server.accept();
                logger.info("New client from {}", client.getRemoteAddress());
                ForkJoinPool.commonPool().submit(() -> readRequestAndRepeat(client, dir));
            }
        }
    }

    public void readRequestAndRepeat(SocketChannel socket, String dir) {

        try (SocketChannel client = socket) {
            ByteBuffer buf = ByteBuffer.allocate(1024);

            for (; ; ) {
                int nr = client.read(buf);

                if (nr == -1)
                    break;

                if (nr > 0) {
                    // ByteBuffer is tricky, you have to flip when switch from read to write, or vice-versa
                    buf.flip();

                    File folder = new File(dir);
                    Charset utf8 = StandardCharsets.UTF_8;

                    String response = display(buf.duplicate(), folder);
                    ByteBuffer byteBufferWrite = utf8.encode(response);
                    client.write(byteBufferWrite);
                    byteBufferWrite.clear();

                    buf.flip();
                    buf.clear();
                    break;
                }
            }
        } catch (IOException e) {
            logger.error("Echo error {}", e);
        }
    }

    public String display(ByteBuffer x, File folder){

        String listFiles = "";
        String listOfFiles = "";
        String output = "";
        String fileName = "";
        String requestIgnoreQuery = "";
        Charset utf8 = StandardCharsets.UTF_8;
        String input = utf8.decode(x).toString();
        System.out.println("input: " + input);
        //input: GET /FileName? HTTP/1.0
        //Host: localhost...

        String inputMessage[] = input.split("\r\n\r\n");
        String header = inputMessage[0];
        String body = "";

        if(inputMessage.length > 1) {
            body = inputMessage[1];
        }

        System.out.println("header: " + header);
        System.out.println("body: " + body);

        //request[1] = /FileName?
        String request[] = header.split("\r\n")[0].split(" ");

        System.out.println("request: " + request[1]);

        if(request[0].equalsIgnoreCase("GET")) {

            //requestIgnoreQuery = /FileName
            requestIgnoreQuery = request[1].substring(0, request[1].lastIndexOf("?"));
            System.out.println("requestIgnoreQuery: " + requestIgnoreQuery);

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

                System.out.println("content: " + content);

                if(!content.isEmpty()) {

                    output = "HTTP/1.0 200 OK" + "\r\n\n" + content + "\r\n\n";

                } else{
                    output = "HTTP/1.0 404 Error Not Found\r\n\n" + "File was not found.";
                }
            }

        } else if(request[0].equalsIgnoreCase("POST")) {

            // /filename
            requestIgnoreQuery = request[1];

            System.out.println("requestIgnoreQuery: " + requestIgnoreQuery);

            if(requestIgnoreQuery.equals("/")){

                output = "HTTP/1.0 403 Error Not Allowed\r\n\n";

            } else if(requestIgnoreQuery.substring(0,1).equals("/")) {

                fileName = requestIgnoreQuery.substring(1);

                System.out.println("fileName: " + fileName);

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
}