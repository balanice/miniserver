package com.force.miniserver;

import com.android.ddmlib.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class ScrTest {

    private Logger logger = LoggerFactory.getLogger(ScrTest.class);

    private IDevice device;
    private static final String adbPath = "/home/force/Android/Sdk/platform-tools/adb";
    private void init() {
        AndroidDebugBridge.init(false);
        AndroidDebugBridge bridge = AndroidDebugBridge.createBridge(adbPath,
                false, 10, TimeUnit.SECONDS);
        int count = 0;
        while (!bridge.hasInitialDeviceList()) {
            try {
                Thread.sleep(1000);
                count++;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (count > 30) break;
        }
        device = bridge.getDevices()[0];
    }

    private void pushServer() {
        String local = "/home/force/Projects/scrcpy/x/server/scrcpy-server";
        String remote = "data/local/tmp/scrcpy-server.jar";
        try {
            device.pushFile(local, remote);
        } catch (IOException | AdbCommandRejectedException |
                TimeoutException | SyncException e) {
            e.printStackTrace();
        }
    }

    private int localPort;
    private static final int PORT_START = 19896;
    private boolean enableTunnel() {
        try {
            device.createForward(PORT_START, "scrcpy", IDevice.DeviceUnixSocketNamespace.ABSTRACT);
            localPort = PORT_START;
            return true;
        } catch (TimeoutException | AdbCommandRejectedException | IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean startServer() {
        String command = adbPath + " shell \"CLASSPATH=/data/local/tmp/scrcpy-server.jar app_process / com.genymobile.scrcpy.Server 1.17 info 0 8000000 0 -1 true - true true 0 false false - - >/dev/null 2>&1 &\"";
        try {
            Runtime.getRuntime().exec(command);
            Thread.sleep(2000);
            return true;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

    private Socket videoSocket;
    private Socket controlSocket;
    private boolean createSocket() {
        try {
            videoSocket = new Socket("localhost", localPort);
            controlSocket = new Socket("localhost", localPort);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void receive() {
        int cacheSize = 1024 * 512;
        byte[] bytes = new byte[1024 * 512];
        int len = 0;
        int offset = 0;
        int headSize = 69;
        int metaSize = 12;
        boolean readHead = false;
        int count = 0;
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(new File("xx.h264"));
            videoSocket = new Socket("localhost", 19896);
            controlSocket = new Socket("localhost", 19896);
            InputStream inputStream = videoSocket.getInputStream();
            while ((len = inputStream.read(bytes, offset, cacheSize - offset)) != -1) {
                logger.info("len: {}", len);
                offset += len;
                if (!readHead) {
                    if (offset >= headSize) {
                        readHead = true;
                        int remind = offset - headSize;
                        if (remind > 0) {
                            System.arraycopy(bytes, headSize, bytes, 0, remind);
                        }
                        offset = remind;
                    }
                } else {
//                    logger.info("{}", Arrays.copyOfRange(bytes, 0, 12));
                    int off = 0;
                    int frameSize = 0;
                    while (offset - off >= metaSize + frameSize) {
                        if (frameSize == 0) {
                            byte[] copy = Arrays.copyOfRange(bytes,
                                    off + metaSize - 4, off + metaSize);
//                            logger.info("copy {}", copy);
                            frameSize = byteArrayToInt(copy);
//                            if (frameSize <= 0) break;
                            continue;
                        }
                        logger.info("frameSize: {}", frameSize);
                        byte[] data = Arrays.copyOfRange(bytes, off + metaSize, off + metaSize + frameSize);
                        outputStream.write(data);
                        off += frameSize + metaSize;
                        count++;
                        frameSize = 0;
                    }
                    int remind = offset - off;
                    if (remind > 0 && off > 0) {
                        System.arraycopy(bytes, off, bytes, 0, remind);
                    }
                    offset = remind;
                }
                if (count > 10) break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (videoSocket != null)
                    videoSocket.close();
                if (controlSocket != null) controlSocket.close();
                if (outputStream != null) {
                    outputStream.flush();
                    outputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void start() {
        init();
//        pushServer();
        receive();
//        if (!enableTunnel()) {
//            logger.error("enable tunnel failed");
//            return;
//        }
//        if (!startServer()) {
//            logger.error("start server failed");
//            return;
//        }
//        if (!createSocket()) {
//            logger.error("create socket failed");
//            return;
//        }
    }

    @Test
    public void t2() {
        byte[] bytes = new byte[] { 0, 0, 126, -31};
        int i = byteArrayToInt(bytes);
        logger.info("i: {}", i);
    }

    // 32493
    public int byteArrayToInt(byte[] b) {
        return  b[3] & 0xFF |
                (b[2] & 0xFF) << 8 |
                (b[1] & 0xFF) << 16 |
                (b[0] & 0xFF) << 24;
    }
}
