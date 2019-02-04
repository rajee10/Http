import java.util.*;
import java.util.List;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class httpc {

    public static void main(String[]args) {

        OptionParser parser = new OptionParser();
        Map headers = new HashMap();
        String method = "";
        String URL = "";
        String data = "";
        String file = "";
        Boolean verbose;
        Boolean redirect;
        String output = "";
        String outputFile="";

        parser.accepts("v", "Enables a verbose output from the command-line.").withOptionalArg();
        parser.accepts("h", "To pass the headers value to your HTTP operation.").withRequiredArg();
        parser.accepts("d", "Associate the body of the HTTP Request with the inline data.").withRequiredArg();
        parser.accepts("f", "Associate the body of the HTTP Request with the data from a given text file.").availableUnless("d").withRequiredArg();
        parser.accepts("r", "Allow your HTTP client to follow the first request with another one to new URL.").withOptionalArg();
        parser.accepts("o", "To write the body of the response to the specified file instead of the console").withRequiredArg();

        OptionSet opts = parser.parse(args);
        HttpcLibrary httpcLibrary = new HttpcLibrary();

        if(args.length < 1) {

            System.exit(0);

        } else if (args[0].equals("help")) {
            output = httpcLibrary.helpRequest(args);

            System.out.println(output);

        } else if (args[0].equalsIgnoreCase("POST") || args[0].equalsIgnoreCase("GET")) {

            verbose = opts.has("v");
            redirect = opts.has("r");

            if (opts.has("h")) {
                headers = getHeaders(opts.valuesOf("h"));
            }

            if (opts.has("o")) {
                outputFile = (String) opts.valueOf("o");
            }

            if (args[0].equalsIgnoreCase("GET")) {
                method = "GET";
            } else if (args[0].equalsIgnoreCase("POST")) {
                method = "POST";

                if (opts.has("d")) {
                    data = (String) opts.valueOf("d");
                }
                else if (opts.has("f")) {
                    file = (String) opts.valueOf("f");
                }
            }

            for (int i = 0; i < args.length; ++i) {
                if (args[i].startsWith("https://") || args[i].startsWith("http://")) {
                    URL = args[i];
                    break;
                }
            }

            output = httpcLibrary.sendRequest(method, URL, file, data, headers, verbose, redirect);

            Writer writer = null;

            if(!outputFile.isEmpty()){
                try {
                    writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), "utf-8"));
                    writer.write(output);
                } catch (Exception e){
                    System.out.println("Error writing to file");
                } finally {
                    try{
                        writer.close();
                    }catch (Exception e){

                    }
                }
            } else {
                System.out.println(output);
            }
        } else {
            System.exit(0);
        }

    }

    public static Map getHeaders(List list){

        Map<String, String> headers = new HashMap();

        for(int i = 0; i < list.size(); i++) {
            String keyAndValue = (String) list.get(i);

            if(keyAndValue.contains(":")) {
                String key = keyAndValue.substring(0, keyAndValue.indexOf(":"));
                String value = keyAndValue.substring(keyAndValue.indexOf(":")+1);

                headers.put(key, value);
            }
        }

        return headers;
    }
}
