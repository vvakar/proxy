package val;

import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;

/**
 * Created by valentin.vakar on 10/8/15.
 */
public class MyProxy {
    private static final int port = 8000;

    public void doWork() throws Exception {
        ServerSocket ss = new ServerSocket(port);
        while (true) {
            System.out.println("Waiting for connections on " + port);
            Socket client = ss.accept();
            new AsyncProcessor(client).start();
        }
    }

    private static final class AsyncProcessor extends Thread {
        private final Socket client;

        public AsyncProcessor(Socket client) {
            this.client = client;
        }

        public void run() {
            final Socket server = new Socket();
            try {
                final InputStream streamFromClient = client.getInputStream();
                final OutputStream streamToClient = client.getOutputStream();

                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(streamFromClient));
                StringBuilder sb = new StringBuilder();
                String targetHost = null;
                int targetPort = 80;
                while (bufferedReader.ready()) {
                    String line = bufferedReader.readLine();
                    System.out.println(line);

                    if (line.toUpperCase().startsWith("HOST:")) {
                        targetHost = line.substring("HOST:".length()).trim();
                        URL url;
                        if (!targetHost.toUpperCase().startsWith("http")) {
                            url = new URL("http://" + targetHost);
                        } else {
                            url = new URL(targetHost);
                        }
                        targetHost = url.getHost();
                        targetPort = url.getPort();
                        if (targetPort == -1) targetPort = 80;
                    }
                    sb.append(line + "\r\n");
                }

                System.out.println("Connecting to " + targetHost + ":" + targetPort);
                server.connect(new InetSocketAddress(targetHost, targetPort));
                final InputStream streamFromServer = server.getInputStream();
                final OutputStream streamToServer = server.getOutputStream();


                System.out.println("Writing to server:");
                System.out.println(sb);
                IOUtils.write(sb.toString(), streamToServer);

                Runnable serverRunnable = new Runnable() {
                    public void run() {
                        try {
                            System.out.println("Streaming FROM server...");
                            IOUtils.copy(streamFromServer, streamToClient);
                            System.out.println("Streaming FROM server Done.");
                        } catch (Exception e) {
                            System.out.println("ERROR reading from server");
                            e.printStackTrace();
                            throw new RuntimeException(e);
                        }
                    }
                };

                Thread serverThread = new Thread(serverRunnable);
                serverThread.setName("Server " + targetHost + ":" + targetPort);
                serverThread.start();


                IOUtils.copy(streamFromClient, streamToServer);

            } catch (Exception e) {
                System.out.println("EXCEPTION: " + e);
                e.printStackTrace();
            } finally {
                try {
                    client.close();
                    server.close();
                    System.out.println("Done.");
                } catch (Exception e) {
                }
            }
        }
    }

    public static void main(String[] asdf) throws Exception {
        new MyProxy().doWork();
    }
}
