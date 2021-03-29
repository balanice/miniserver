package com.force.miniserver;

import com.android.ddmlib.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.websocket.Session;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Service
public class DeviceService {

    private static final Logger logger = LoggerFactory.getLogger(DeviceService.class);

    private AndroidDebugBridge bridge;
    private ScreenRunner runner;

    public DeviceService() {
        AndroidDebugBridge.init(false);
        bridge = AndroidDebugBridge.createBridge("/home/force/Android/Sdk/platform-tools/adb",
                false, 10, TimeUnit.SECONDS);
        bridge.startAdb(10, TimeUnit.SECONDS);
        waitForDevice(bridge);
    }

    public void startScreen(Session session) {
        logger.info("startScreen");
        stopMinicap();
        if (!startMinicap()) {
            logger.error("start minicap failed, try again!");
            try {
                session.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
        if (runner == null) {
            runner = new ScreenRunner();
        }
        runner.start(session);
        new Thread(runner).start();
    }

    public void stopScreen() {
        logger.info("stopScreen");
        if (runner != null) {
            runner.stop();
        }
    }

    private boolean stopMinicap() {
        boolean b = bridge.hasInitialDeviceList();
        if (b) {
            IDevice device = bridge.getDevices()[0];
            String serialNumber = device.getSerialNumber();
            logger.info("sn: {}", serialNumber);
            CollectingOutputReceiver receiver = new CollectingOutputReceiver();
            try {
                device.executeShellCommand("ps|grep mini", receiver);
            } catch (TimeoutException | AdbCommandRejectedException |
                    ShellCommandUnresponsiveException | IOException e) {
                e.printStackTrace();
            }
            String output = receiver.getOutput();
            if (output != null) {
                String[] split = output.split("\\s+");
                if (split.length > 1) {
                    String cmd = String.format("kill -9 %s", split[1]);
                    try {
                        logger.info("kill minicap: {}", cmd);
                        device.executeShellCommand(cmd, receiver);
                        device.removeForward(1313, "minicap", IDevice.DeviceUnixSocketNamespace.ABSTRACT);
                        Thread.sleep(3000);
                        return true;
                    } catch (TimeoutException | AdbCommandRejectedException | ShellCommandUnresponsiveException | IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return false;
    }

    private boolean startMinicap() {
        boolean b = bridge.hasInitialDeviceList();
        if (b) {
            IDevice device = bridge.getDevices()[0];
            String start = "LD_LIBRARY_PATH=/data/local/tmp /data/local/tmp/minicap -P 1080x1920@1080x1920/0 -S >/dev/null 2>&1 &";
            logger.info("start minicap: {}", start);
            CollectingOutputReceiver receiver = new CollectingOutputReceiver();
            try {
                device.executeShellCommand(start, receiver);
                device.createForward(1313, "minicap",
                        IDevice.DeviceUnixSocketNamespace.ABSTRACT);
                Thread.sleep(3000);
                return checkServer(device);
            } catch (TimeoutException | AdbCommandRejectedException |
                    ShellCommandUnresponsiveException | IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private boolean checkServer(IDevice device) {
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        try {
            device.executeShellCommand("ps|grep minicap", receiver);
        } catch (TimeoutException | AdbCommandRejectedException |
                ShellCommandUnresponsiveException | IOException e) {
            e.printStackTrace();
        }
        String output = receiver.getOutput();
        logger.info(output);
        if (output != null) {
            String[] split = output.split("\\s+");
            if (split.length > 1) {
                logger.info("pid: {}", split[1]);
                return true;
            }
        }
        return false;
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
            int count = 0;
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
                            logger.info("imageSize: {}, index: {}, offset: {}", imageSize, index, offset);
                            byte[] bytes = Arrays.copyOfRange(cache, offset, offset + imageSize);
                            ByteBuffer buffer = ByteBuffer.wrap(bytes);
//                            ByteBuffer buffer = ByteBuffer.wrap(cache, offset, imageSize);
                            session.getBasicRemote().sendBinary(buffer);

//                            ByteArrayInputStream stream = new ByteArrayInputStream(cache, offset, imageSize);
                            ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
                            BufferedImage i = ImageIO.read(stream);
                            String filename = String.format("%s%s%d.jpg", "/home/force/Projects/temp", File.separator, count++);
                            ImageIO.write(i, "JPG", new File(filename));
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
