import joptsimple.OptionParser;
import joptsimple.OptionSet;

public class httpfs {

    public static void main(String[]args) {

        OptionParser parser = new OptionParser();
        parser.accepts("p", "Specifies the port number that the server will listen and serve at.").withOptionalArg().defaultsTo("8080");
        parser.accepts("d", "Specifies the directory that the server will use to read/write requested files.").withRequiredArg().defaultsTo(".");
        parser.accepts("v", "Prints debugging messages.").withOptionalArg();

        OptionSet opts = parser.parse(args);

        if (args[0].equals("help")) {
            System.out.println("\nhttpfs is a simple file server.\n");
            System.out.println("Usage: httpfs get [-v] [-p PORT] [-d PATH-TO-DIR]\n\n");
            System.out.println("   -v   Prints debugging messages.");
            System.out.println("   -p   Specifies the port number that the server will listen and serve at. Default is 8080.");
            System.out.println("   -d   Specifies the directory that the server will use to read/write requested files. " +
                    "Default is the current directory when launching the application.\n");
        }
        else {
            int port = Integer.parseInt((String) opts.valueOf("p"));
            String directory = (String) opts.valueOf("d");
            Boolean verbose = opts.has("v");

            try {
                HttpfsLibrary server = new HttpfsLibrary();
                server.listenAndServe(port, directory);
            } catch (Exception e) {
                System.out.println("Error while listen and serve:");
            }
        }
    }
}