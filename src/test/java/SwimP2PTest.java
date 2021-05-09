import io.friday.p2p.Impl.DefaultP2PNode;
import io.friday.p2p.Impl.SwimP2PNode;
import io.friday.p2p.entity.FileInfo;
import io.friday.transport.entity.Address;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;

public class SwimP2PTest {
    public static void main(String[] args) throws Exception {
        try {
            int port = Integer.parseInt(System.getenv("port"));
            SwimP2PNode p2PNode = new SwimP2PNode("localhost", port);
            p2PNode.init();
            p2PNode.start();

            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
            while (true){
                String line = input.readLine();
                String command = line.split(" ")[0];
                String detail = null;
                System.out.println("命令: " + line);
                switch (command) {
                    case "join":
                        detail = line.split(" ")[1];
                        int destPort = Integer.parseInt(detail);
                        p2PNode.join(new Address("localhost", destPort));
                        break;
                    case "share":
                        detail = line.split(" ")[1];
                        p2PNode.share(new ArrayList<>(Arrays.asList(new FileInfo(detail))));
                        break;
                    case "leave":
                        p2PNode.leave();
                        break;
                    case "list":
                        p2PNode.list();
                        break;
                    case "refuse":
                        detail = line.split(" ")[1];
                        int refusePort = Integer.parseInt(detail);
                        p2PNode.addRefuseAddress(new Address("localhost", refusePort));
                        break;
                    default:
                        System.out.println("命令不支持： " + command);

                }

            }
//            SpringApplication.run(RegistryApplication.class, args);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}