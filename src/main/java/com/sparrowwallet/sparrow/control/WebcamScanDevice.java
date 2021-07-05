package com.sparrowwallet.sparrow.control;

import com.github.sarxos.webcam.*;
import com.github.sarxos.webcam.ds.buildin.natives.Device;
import com.github.sarxos.webcam.ds.buildin.natives.DeviceList;
import com.github.sarxos.webcam.ds.buildin.natives.OpenIMAJGrabber;
import org.bridj.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.nio.ByteBuffer;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("deprecation")
public class WebcamScanDevice implements WebcamDevice, WebcamDevice.BufferAccess, Runnable, WebcamDevice.FPSSource {
    private static final Logger LOG = LoggerFactory.getLogger(WebcamScanDevice.class);
    private static final int DEVICE_BUFFER_SIZE = 5;
    private static final Dimension[] DIMENSIONS;
    private static final int[] BAND_OFFSETS;
    private static final int[] BITS;
    private static final int[] OFFSET;
    private static final int DATA_TYPE = 0;
    private static final ColorSpace COLOR_SPACE;
    public static final int SCAN_LOOP_WAIT_MILLIS = 100;
    private int timeout = 5000;
    private OpenIMAJGrabber grabber = null;
    private Device device = null;
    private Dimension size = null;
    private ComponentSampleModel smodel = null;
    private ColorModel cmodel = null;
    private boolean failOnSizeMismatch = false;
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final AtomicBoolean open = new AtomicBoolean(false);
    private final AtomicBoolean fresh = new AtomicBoolean(false);
    private Thread refresher = null;
    private String name = null;
    private String id = null;
    private String fullname = null;
    private long t1 = -1L;
    private long t2 = -1L;
    private volatile double fps = 0.0D;

    protected WebcamScanDevice(Device device) {
        this.device = device;
        this.name = device.getNameStr();
        this.id = device.getIdentifierStr();
        this.fullname = String.format("%s %s", this.name, this.id);
    }

    public String getName() {
        return this.fullname;
    }

    public String getDeviceName() {
        return this.name;
    }

    public String getDeviceId() {
        return this.id;
    }

    public Device getDeviceRef() {
        return this.device;
    }

    public Dimension[] getResolutions() {
        return DIMENSIONS;
    }

    public Dimension getResolution() {
        if (this.size == null) {
            this.size = this.getResolutions()[0];
        }

        return this.size;
    }

    public void setResolution(Dimension size) {
        if (size == null) {
            throw new IllegalArgumentException("Size cannot be null");
        } else if (this.open.get()) {
            throw new IllegalStateException("Cannot change resolution when webcam is open, please close it first");
        } else {
            this.size = size;
        }
    }

    public ByteBuffer getImageBytes() {
        if (this.disposed.get()) {
            LOG.debug("Webcam is disposed, image will be null");
            return null;
        } else if (!this.open.get()) {
            LOG.debug("Webcam is closed, image will be null");
            return null;
        } else {
            if (this.fresh.compareAndSet(false, true)) {
                this.updateFrameBuffer();
            }

            LOG.trace("Webcam grabber get image pointer");
            Pointer<Byte> image = this.grabber.getImage();
            this.fresh.set(false);
            if (image == null) {
                LOG.warn("Null array pointer found instead of image");
                return null;
            } else {
                int length = this.size.width * this.size.height * 3;
                LOG.trace("Webcam device get buffer, read {} bytes", length);
                return image.getByteBuffer((long)length);
            }
        }
    }

    public void getImageBytes(ByteBuffer target) {
        if (this.disposed.get()) {
            LOG.debug("Webcam is disposed, image will be null");
        } else if (!this.open.get()) {
            LOG.debug("Webcam is closed, image will be null");
        } else {
            int minSize = this.size.width * this.size.height * 3;
            int curSize = target.remaining();
            if (minSize > curSize) {
                throw new IllegalArgumentException(String.format("Not enough remaining space in target buffer (%d necessary vs %d remaining)", minSize, curSize));
            } else {
                if (this.fresh.compareAndSet(false, true)) {
                    this.updateFrameBuffer();
                }

                LOG.trace("Webcam grabber get image pointer");
                Pointer<Byte> image = this.grabber.getImage();
                this.fresh.set(false);
                if (image == null) {
                    LOG.warn("Null array pointer found instead of image");
                } else {
                    LOG.trace("Webcam device read buffer {} bytes", minSize);
                    image = image.validBytes((long)minSize);
                    image.getBytes(target);
                }
            }
        }
    }

    public BufferedImage getImage() {
        ByteBuffer buffer = this.getImageBytes();
        if (buffer == null) {
            LOG.error("Images bytes buffer is null!");
            return null;
        } else {
            byte[] bytes = new byte[this.size.width * this.size.height * 3];
            byte[][] data = new byte[][]{bytes};
            buffer.get(bytes);
            DataBufferByte dbuf = new DataBufferByte(data, bytes.length, OFFSET);
            WritableRaster raster = Raster.createWritableRaster(this.smodel, dbuf, (Point)null);
            BufferedImage bi = new BufferedImage(this.cmodel, raster, false, (Hashtable)null);
            bi.flush();
            return bi;
        }
    }

    public void open() {
        if (!this.disposed.get()) {
            LOG.debug("Opening webcam device {}", this.getName());
            if (this.size == null) {
                this.size = this.getResolutions()[0];
            }

            if (this.size == null) {
                throw new RuntimeException("The resolution size cannot be null");
            } else {
                LOG.debug("Webcam device {} starting session, size {}", this.device.getIdentifierStr(), this.size);
                this.grabber = new OpenIMAJGrabber();
                DeviceList list = (DeviceList)this.grabber.getVideoDevices().get();
                Iterator var2 = list.asArrayList().iterator();

                while(var2.hasNext()) {
                    Device d = (Device)var2.next();
                    d.getNameStr();
                    d.getIdentifierStr();
                }

                boolean started = this.grabber.startSession(this.size.width, this.size.height, 50, Pointer.pointerTo(this.device));
                if (!started) {
                    throw new WebcamException("Cannot start native grabber!");
                } else {
                    this.grabber.setTimeout(this.timeout);
                    LOG.debug("Webcam device session started");
                    Dimension size2 = new Dimension(this.grabber.getWidth(), this.grabber.getHeight());
                    int w1 = this.size.width;
                    int w2 = size2.width;
                    int h1 = this.size.height;
                    int h2 = size2.height;
                    if (w1 != w2 || h1 != h2) {
                        if (this.failOnSizeMismatch) {
                            throw new WebcamException(String.format("Different size obtained vs requested - [%dx%d] vs [%dx%d]", w1, h1, w2, h2));
                        }

                        Object[] args = new Object[]{w1, h1, w2, h2, w2, h2};
                        LOG.warn("Different size obtained vs requested - [{}x{}] vs [{}x{}]. Setting correct one. New size is [{}x{}]", args);
                        this.size = new Dimension(w2, h2);
                    }

                    this.smodel = new ComponentSampleModel(0, this.size.width, this.size.height, 3, this.size.width * 3, BAND_OFFSETS);
                    this.cmodel = new ComponentColorModel(COLOR_SPACE, BITS, false, false, 1, 0);
                    LOG.debug("Clear memory buffer");
                    this.clearMemoryBuffer();
                    LOG.debug("Webcam device {} is now open", this);
                    this.open.set(true);
                    this.refresher = this.startFramesRefresher();
                }
            }
        }
    }

    private void clearMemoryBuffer() {
        for(int i = 0; i < 5; ++i) {
            this.grabber.nextFrame();
        }

    }

    private Thread startFramesRefresher() {
        Thread refresher = new Thread(this, String.format("frames-refresher-[%s]", this.id));
        refresher.setUncaughtExceptionHandler(WebcamExceptionHandler.getInstance());
        refresher.setDaemon(true);
        refresher.start();
        return refresher;
    }

    public void close() {
        if (this.open.compareAndSet(true, false)) {
            LOG.debug("Closing webcam device");
            this.grabber.stopSession();
        }
    }

    public void dispose() {
        if (this.disposed.compareAndSet(false, true)) {
            LOG.debug("Disposing webcam device {}", this.getName());
            this.close();
        }
    }

    public void setFailOnSizeMismatch(boolean fail) {
        this.failOnSizeMismatch = fail;
    }

    public boolean isOpen() {
        return this.open.get();
    }

    public int getTimeout() {
        return this.timeout;
    }

    public void setTimeout(int timeout) {
        if (this.isOpen()) {
            throw new WebcamException("Timeout must be set before webcam is open");
        } else {
            this.timeout = timeout;
        }
    }

    private void updateFrameBuffer() {
        LOG.trace("Next frame");
        if (this.t1 == -1L || this.t2 == -1L) {
            this.t1 = System.currentTimeMillis();
            this.t2 = System.currentTimeMillis();
        }

        int result = (new WebcamScanDevice.NextFrameTask(this)).nextFrame();
        this.t1 = this.t2;
        this.t2 = System.currentTimeMillis();
        this.fps = (4.0D * this.fps + (double)(1000L / (this.t2 - this.t1 + 1L))) / 5.0D;
        if (result == -1) {
            LOG.error("Timeout when requesting image!");
        } else if (result < -1) {
            LOG.error("Error requesting new frame!");
        }

    }

    public void run() {
        do {
            try {
                Thread.sleep(SCAN_LOOP_WAIT_MILLIS);
            } catch(InterruptedException e) {
                //ignore
            }

            if (Thread.interrupted()) {
                LOG.debug("Refresher has been interrupted");
                return;
            }

            if (!this.open.get()) {
                LOG.debug("Cancelling refresher");
                return;
            }

            this.updateFrameBuffer();
        } while(this.open.get());

    }

    public double getFPS() {
        return this.fps;
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) {
            return true;
        }
        if(o == null || getClass() != o.getClass()) {
            return false;
        }
        WebcamScanDevice that = (WebcamScanDevice) o;
        return Objects.equals(fullname, that.fullname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullname);
    }

    static {
        DIMENSIONS = new Dimension[]{WebcamResolution.QQVGA.getSize(), WebcamResolution.QVGA.getSize(), WebcamResolution.VGA.getSize()};
        BAND_OFFSETS = new int[]{0, 1, 2};
        BITS = new int[]{8, 8, 8};
        OFFSET = new int[]{0};
        COLOR_SPACE = ColorSpace.getInstance(1000);
    }

    private class NextFrameTask extends WebcamTask {
        private final AtomicInteger result = new AtomicInteger(0);

        public NextFrameTask(WebcamDevice device) {
            super(device);
        }

        public int nextFrame() {
            try {
                this.process();
            } catch (InterruptedException var2) {
                WebcamScanDevice.LOG.debug("Image buffer request interrupted", var2);
            }

            return this.result.get();
        }

        protected void handle() {
            WebcamScanDevice device = (WebcamScanDevice)this.getDevice();
            if (device.isOpen()) {
                try {
                    Thread.sleep(SCAN_LOOP_WAIT_MILLIS);
                } catch(InterruptedException e) {
                    //ignore
                }

                this.result.set(WebcamScanDevice.this.grabber.nextFrame());
                WebcamScanDevice.this.fresh.set(true);
            }
        }
    }
}
