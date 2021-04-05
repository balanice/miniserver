package com.force.miniserver;

import com.android.ddmlib.*;
import com.android.sdklib.AndroidVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.websocket.Session;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static com.force.miniserver.Utils.empty;

@Service
public class DeviceService {

    private static final Logger logger = LoggerFactory.getLogger(DeviceService.class);

    private static final String ADB_HOME = "/home/force/Android/Sdk/platform-tools/adb";

    private AndroidDebugBridge bridge;
    private ScreenRunner runner;
    private IDevice device;

    public DeviceService() {
        AndroidDebugBridge.init(false);
        bridge = AndroidDebugBridge.createBridge(ADB_HOME,
                false, 10, TimeUnit.SECONDS);
        bridge.startAdb(10, TimeUnit.SECONDS);
        waitForDevice(bridge);
        device = bridge.getDevices()[0];
    }

    public boolean startScreen(Session session) {
        logger.info("startScreen");
        killMinicap();
        if (!pushServer()) {
            logger.info("push server failed");
            return false;
        }
        if (!enableForward()) {
            logger.info("create forward failed");
            return false;
        }
        if (!executeServer()) {
            logger.error("start minicap failed, try again!");
            return false;
        }
        if (runner == null) {
            runner = new ScreenRunner();
        }
        runner.start(session);
        new Thread(runner).start();
        return true;
    }

    public boolean pushServer() {
        String userDir = System.getProperty("user.dir");

        String bin_format = "minicap/%s/minicap";
        String soFormat = "minicap/libs/android-%d/%s/minicap.so";

        AndroidVersion version = device.getVersion();
        String abi = device.getProperty("ro.product.cpu.abi");

        String binResource = String.format(bin_format, abi);
        logger.info(binResource);
        String soResource = String.format(soFormat, version.getApiLevel(), abi);
        logger.info(soResource);

        String localBin = userDir + File.separator + "tmp" + File.separator + binResource;
        String localSo = userDir + File.separator + "tmp" + File.separator + soResource;

        if (!Utils.resourceToFile(binResource, localBin)) {
            logger.error("bin resource to file failed");
            return false;
        }
        if (!Utils.resourceToFile(soResource, localSo)) {
            logger.error("so resource to file failed");
            return false;
        }

        try {
            device.pushFile(localBin, "/data/local/tmp/minicap");
            device.pushFile(localSo, "/data/local/tmp/minicap.so");
            executeShellCommand(device, "chmod +x /data/local/tmp/minicap");
            return true;
        } catch (IOException | AdbCommandRejectedException | TimeoutException | SyncException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void stopScreen() {
        logger.info("stopScreen");
        if (runner != null) {
            runner.stop();
        }
    }

    private boolean enableForward() {
        try {
            device.createForward(1313, "minicap",
                    IDevice.DeviceUnixSocketNamespace.ABSTRACT);
            return true;
        } catch (TimeoutException | AdbCommandRejectedException | IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean disableForward() {
        try {
            device.removeForward(1313, "minicap", IDevice.DeviceUnixSocketNamespace.ABSTRACT);
            return true;
        } catch (TimeoutException | AdbCommandRejectedException | IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void killMinicap() {
        String pid = getPid();
        if (!empty(pid)) {
            String command = String.format("kill -9 %s", pid);
            logger.info("kill: {}", command);
            executeShellCommand(device, command);
        }
        disableForward();
    }

    private String getPid() {
        String s = executeShellCommand(device, "ps -ef|grep minicap");
        if (empty(s)) return null;
        String[] split = s.split("\n");
        for (String ss : split) {
            if (ss.contains("minicap -P")) {
                String[] split1 = ss.split("\\s+");
                if (split1.length > 1) {
                    return split1[1];
                }
            }
        }
        return null;
    }

    public String executeShellCommand(IDevice device, String command) {
        if (empty(command)) return null;
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        try {
            device.executeShellCommand(command, receiver);
            return receiver.getOutput();
        } catch (TimeoutException | AdbCommandRejectedException | ShellCommandUnresponsiveException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private boolean executeServer() {
        // start minicap
        String start = String.format("%s -s %s shell LD_LIBRARY_PATH=/data/local/tmp /data/local/tmp/minicap -P 1080x1920@1080x1920/0 -S >/dev/null 2>&1 &",
                ADB_HOME, device.getSerialNumber());
        executeAdbCommand(start);
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return true;
    }

    private void executeAdbCommand(String command) {
        logger.info(command);
        try {
            Runtime.getRuntime().exec(command);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void waitForDevice(AndroidDebugBridge bridge) {
        int count = 0;
        while (!bridge.hasInitialDeviceList()) {
            try {
                Thread.sleep(100);
                count++;
            } catch (InterruptedException ignored) {
            }
            if (count > 300) {
                System.err.print("Time out");
                break;
            }
        }
    }

    private static class ScreenRunner implements Runnable {

        private static final int CACHE_SIZE = 1024 * 1024;
        private boolean running;
        private Session session;

        public void start(Session session) {
            this.session = session;
            running = true;
        }

        public void stop() {
            running = false;
        }

        @Override
        public void run() {
            Socket socket = new Socket();
            InetSocketAddress address = new InetSocketAddress("127.0.0.1", 1313);
            byte[] cache = new byte[CACHE_SIZE];
            boolean readHead = false;
            int index = 0;
            int imageSize = 0;
            try {
                socket.connect(address);
                InputStream inputStream = socket.getInputStream();
                int read;
                while (running && (read = inputStream.read(cache, index, CACHE_SIZE - index)) != -1) {
                    if (!readHead) {
                        int version = cache[0] & 0xFF;
                        int size = cache[1] & 0xFF;
                        int pid = Utils.byteArrayToInt(Arrays.copyOfRange(cache, 2, 6));
                        int readWidth = Utils.byteArrayToInt(Arrays.copyOfRange(cache, 6, 10));
                        int realHeight = Utils.byteArrayToInt(Arrays.copyOfRange(cache, 10, 14));
                        logger.info("version: {}, size: {}, pid: {}, realWidth: {}, realHeight:{}",
                                version, size, pid, readWidth, realHeight);
                        readHead = true;
                    } else {
                        index += read;

                        int offset = 0;
                        boolean flag = false;
                        while (index - offset >= imageSize + 4) {
                            if (imageSize == 0) {
                                imageSize = Utils.byteArrayToInt(Arrays.copyOfRange(cache, offset, offset + 4));
                                if (imageSize < 0) {
                                    logger.info("imageSize: {}", imageSize);
                                    break;
                                }
                                continue;
                            }
                            offset += 4;
                            byte[] bytes = Arrays.copyOfRange(cache, offset, offset + imageSize);
                            ByteBuffer buffer = ByteBuffer.wrap(bytes);
                            session.getBasicRemote().sendBinary(buffer);

                            // Save image to file.
//                            ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
//                            BufferedImage i = ImageIO.read(stream);
//                            String filename = String.format("%s%s%d.jpg", "/home/force/Projects/temp", File.separator, count++);
//                            ImageIO.write(i, "JPG", new File(filename));
                            offset += imageSize;
                            imageSize = 0;
                            flag = true;
                        }

                        if (flag) {
                            for (int i = 0, j = offset; i < index - offset; i++, j++) {
                                cache[i] = cache[j];
                            }
                            index -= offset;
                        }
                        if (imageSize < 0) {
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            logger.info("run end");
        }

    }
}
