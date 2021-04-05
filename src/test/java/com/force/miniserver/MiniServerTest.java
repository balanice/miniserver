package com.force.miniserver;

import com.android.ddmlib.*;
import com.android.sdklib.AndroidVersion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static com.force.miniserver.Utils.empty;

public class MiniServerTest {

    private static final Logger logger = LoggerFactory.getLogger(MiniServerTest.class);

    private static final int CACHE_SIZE = 1024 * 1024;

//    @Test
    public void testDecode() {
        init();
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
            while ((read = inputStream.read(cache, index, CACHE_SIZE - index)) != -1) {
                if (!readHead) {
                    int version = cache[0] & 0xFF;
                    int size = cache[1] & 0xFF;
                    int pid = byteArrayToInt(Arrays.copyOfRange(cache, 2, 6));
                    int readWidth = byteArrayToInt(Arrays.copyOfRange(cache, 6, 10));
                    int realHeight = byteArrayToInt(Arrays.copyOfRange(cache, 10, 14));
                    logger.info("version: {}, size: {}, pid: {}, realWidth: {}, realHeight:{}",
                            version, size, pid, readWidth, realHeight);
                    readHead = true;
                } else {
                    index += read;

                    int offset = 0;
                    boolean flag = false;
                    while (index - offset >= imageSize + 4) {
                        if (imageSize == 0) {
                            imageSize = byteArrayToInt(Arrays.copyOfRange(cache, offset, offset + 4));
                            if (imageSize < 0) {
                                logger.info("imageSize: {}", imageSize);
                                break;
                            }
                            continue;
                        }
                        offset += 4;
                        logger.info("imageSize: {}, index: {}, offset: {}", imageSize, index, offset);
                        ByteArrayInputStream stream = new ByteArrayInputStream(cache, offset, imageSize);
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
                if (count >= 50) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.info("run end");
        Assertions.assertTrue(count >= 50);
    }

//    @Test
    public void testCopy() {
        byte[] bytes1 = intToByteArray(1920);
        for (byte b : bytes1) {
            logger.info("{}", b);
        }
        int i = byteArrayToInt(bytes1);
        logger.info("int: {}", i);
        byte[] bytes2 = intToByteArray(1080);
        for (byte b : bytes2) {
            logger.info("{}", b);
        }
        int i2 = byteArrayToInt(bytes2);
        logger.info("int: {}", i2);
    }

    private boolean checkServer(IDevice device) {
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        try {
            device.executeShellCommand("ps -A|grep mini", receiver);
        } catch (TimeoutException | AdbCommandRejectedException |
                ShellCommandUnresponsiveException | IOException e) {
            e.printStackTrace();
        }
        String output = receiver.getOutput();
        if (output != null) {
            String[] split = output.split("\\s+");
            if (split.length > 1) {
                logger.info("pid: {}", split[1]);
                return true;
            }
        }
        return false;
    }

    private int PORT_FIRST = 28963;
    private int PORT_END = 28999;
    private int localPort;

    private void killMinicap(IDevice device) {
        String pid = getPid(device);
        if (!empty(pid)) {
            String command = String.format("kill -9 %s", pid);
            logger.info("kill: {}", command);
            executeShellCommand(device, command);
        }
    }

    private String getPid(IDevice device) {
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



    private void init() {
        AndroidDebugBridge.init(false);
        AndroidDebugBridge bridge = AndroidDebugBridge.createBridge();
        waitForDevice(bridge);
        boolean b = bridge.hasInitialDeviceList();
        if (b) {
            IDevice device = bridge.getDevices()[0];
            String serialNumber = device.getSerialNumber();
            logger.info("sn: {}", serialNumber);
            CollectingOutputReceiver receiver = new CollectingOutputReceiver();
            try {
                device.executeShellCommand("ps -A|grep mini", receiver);
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
                    } catch (TimeoutException | AdbCommandRejectedException | ShellCommandUnresponsiveException | IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            // start minicap
            String start = "LD_LIBRARY_PATH=/data/local/tmp /data/local/tmp/minicap -P 1080x1920@1080x1920/0 -S >/dev/null 2>&1 &";
            logger.info("start minicap: {}", start);
            try {
                device.executeShellCommand(start, receiver);
                device.createForward(1313, "minicap",
                        IDevice.DeviceUnixSocketNamespace.ABSTRACT);
                Thread.sleep(3000);
                checkServer(device);
            } catch (TimeoutException | AdbCommandRejectedException |
                    ShellCommandUnresponsiveException | IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static boolean waitForDevice(AndroidDebugBridge bridge) {
        int count = 0;
        while (!bridge.hasInitialDeviceList()) {
            try {
                Thread.sleep(100);
                count++;
            } catch (InterruptedException ignored) {
            }
            if (count > 300) {
                System.err.print("Time out");
                return false;
            }
        }
        return true;
    }

    public static int byteArrayToInt(byte[] b) {
        return (b[0] & 0xFF) |
                (b[1] & 0xFF) << 8 |
                (b[2] & 0xFF) << 16 |
                (b[3] & 0xFF) << 24;
    }

    public static byte[] intToByteArray(int a) {
        return new byte[]{
                (byte) (a & 0xFF),
                (byte) ((a >> 8) & 0xFF),
                (byte) ((a >> 16) & 0xFF),
                (byte) ((a >> 24) & 0xFF)
        };
    }

//    @Test
    public void testBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(1080);
        buffer.rewind();
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            bytes[i] = buffer.get(i);
            logger.info("{}", buffer.get(i));
        }
        int i = byteArrayToInt(bytes);
        logger.info("{}", i);
    }

    private static final String MINICAP_BIN = "/data/local/tmp/minicap";
    private static final String MINICAP_SO = "/data/local/tmp/minicap.so";
    private static final String ADB_HOME = "/home/force/Android/Sdk/platform-tools/adb";

    @Test
    public void testVersion() {
        AndroidDebugBridge.init(false);
        AndroidDebugBridge bridge = AndroidDebugBridge.createBridge("/home/force/Android/Sdk/platform-tools/adb",
                false, 10, TimeUnit.SECONDS);
        if (!waitForDevice(bridge)) {
            logger.error("No available device");
            return;
        }
        IDevice device = bridge.getDevices()[0];
        killMinicap(device);
        if (!pushServer(device)) {
            logger.error("push server failed");
            return;
        }
        executeServer(device);
        String pid = getPid(device);
        logger.info("pid: {}", pid);
    }

    public boolean pushServer(IDevice device) {
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

        if (!resourceToFile(binResource, localBin)) {
            logger.error("bin resource to file failed");
            return false;
        }
        if (!resourceToFile(soResource, localSo)) {
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

    public boolean resourceToFile(String resourcePath, String filePath) {
        File file = new File(filePath);
        if (file.exists() && file.isFile()) {
            logger.info("file exists: {}", filePath);
            return true;
        }
        ClassPathResource resource = new ClassPathResource(resourcePath);
        File parent = file.getParentFile();
        logger.info(parent.getAbsolutePath());
        if (!parent.mkdirs()) {
            logger.error("make dirs failed");
            return false;
        }
        boolean result = false;
        FileOutputStream outputStream = null;
        InputStream inputStream = null;
        byte[] bytes = new byte[1024 * 512];
        int len;
        try {
            inputStream = resource.getInputStream();
            outputStream = new FileOutputStream(file);
            while ((len = inputStream.read(bytes)) != -1) {
                outputStream.write(bytes, 0, len);
            }
            result = true;
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        } finally {
            try {
                if (outputStream != null) outputStream.close();
                if (inputStream != null) inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    private boolean executeServer(IDevice device) {
        // start minicap
        String start = String.format("%s -s %s shell LD_LIBRARY_PATH=/data/local/tmp /data/local/tmp/minicap -P 1080x1920@1080x1920/0 -S >/dev/null 2>&1 &",
                ADB_HOME, device.getSerialNumber());
        executeAdbCommand(start);
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
}
