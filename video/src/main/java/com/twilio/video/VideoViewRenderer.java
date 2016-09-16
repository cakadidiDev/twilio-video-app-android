package com.twilio.video;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;

import org.webrtc.SurfaceViewRenderer;
import org.webrtc.RendererCommon.ScalingType;
import org.webrtc.RendererCommon.RendererEvents;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;

/**
 * A VideoViewRenderer receives frames from a {@link VideoTrack} and
 * renders them to a view.
 *
 * @deprecated Use {@link VideoView} instead
 */
@Deprecated
public class VideoViewRenderer implements VideoRenderer {
    // Used to ensure that our renderer has a means to post to main thread
    // for renderer events
    private final Handler uiThreadHandler = new Handler(Looper.getMainLooper());

    private final RendererEvents internalEventListener = new RendererEvents() {
        @Override
        public void onFirstFrameRendered() {
            refreshRenderer();
            if (listener != null) {
                listener.onFirstFrame();
            }
        }

        @Override
        public void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation) {
            refreshRenderer();
            if (listener != null) {
                listener.onFrameDimensionsChanged(videoWidth, videoHeight, rotation);
            }
        }
    };

    private final SurfaceViewRenderer surfaceViewRenderer;
    private boolean mirror = false;
    private VideoScaleType videoScaleType = VideoScaleType.ASPECT_FIT;
    private VideoRenderer.Listener listener;

    /**
     * Create a video view renderer that will display frames in
     * the provided container
     * @param context Activity context
     * @param container The view where the frames should be rendered
     */
    public VideoViewRenderer(Context context,
                             ViewGroup container) {
        this.surfaceViewRenderer = new SurfaceViewRenderer(context);
        setupRenderer(context, container);
    }

    public boolean getMirror() {
        return mirror;
    }

    public void setMirror(boolean mirror) {
        this.mirror = mirror;
        surfaceViewRenderer.setMirror(mirror);
        refreshRenderer();
    }

    public VideoScaleType getVideoScaleType() {
        return videoScaleType;
    }

    public void setVideoScaleType(VideoScaleType videoScaleType) {
        this.videoScaleType = videoScaleType;
        surfaceViewRenderer.setScalingType(convertToWebRtcScaleType(videoScaleType));
        refreshRenderer();
    }

    public void setListener(VideoRenderer.Listener listener) {
        this.listener = listener;
    }

    /**
     * Releases resources associated with the video renderer
     */
    public void release() {
        surfaceViewRenderer.release();
    }

    @Override
    public void renderFrame(I420Frame frame) {
        surfaceViewRenderer.renderFrame(convertToWebRtcFrame(frame));
    }

    /**
     * Controls placement of the video render relative to other surface
     *
     * @param overlaySurface if true, video renderer is placed on top of another video renderer
     *                       in the window (but still behind window itself)
     */
    public void applyZOrder(boolean overlaySurface) {
        surfaceViewRenderer.setZOrderMediaOverlay(overlaySurface);
    }

    private void setupRenderer(final Context context, final ViewGroup container) {
        container.addView(surfaceViewRenderer);
        surfaceViewRenderer.init(EglBaseProvider.provideEglBase().getEglBaseContext(),
                internalEventListener);
        surfaceViewRenderer.setScalingType(convertToWebRtcScaleType(videoScaleType));
    }

    private void refreshRenderer() {
        uiThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                if (surfaceViewRenderer != null) {
                    surfaceViewRenderer.requestLayout();
                }
            }
        });
    }

    private ScalingType convertToWebRtcScaleType(VideoScaleType videoScaleType) {
        switch (videoScaleType) {
            case ASPECT_FIT:
                return ScalingType.SCALE_ASPECT_FIT;
            case ASPECT_FILL:
                return ScalingType.SCALE_ASPECT_FILL;
            case ASPECT_BALANCED:
                return ScalingType.SCALE_ASPECT_BALANCED;
            default:
                return ScalingType.SCALE_ASPECT_FIT;
        }
    }

    private org.webrtc.VideoRenderer.I420Frame convertToWebRtcFrame(I420Frame frame) {
        try {
            Constructor<org.webrtc.VideoRenderer.I420Frame> i420FrameConstructor =
                    org.webrtc.VideoRenderer.I420Frame.class
                            .getDeclaredConstructor(int.class,
                                    int.class,
                                    int.class,
                                    int[].class,
                                    ByteBuffer[].class,
                                    long.class);
            i420FrameConstructor.setAccessible(true);
            return i420FrameConstructor.newInstance(frame.width,
                    frame.height,
                    frame.rotationDegree,
                    frame.yuvStrides,
                    frame.yuvPlanes,
                    frame.nativeFramePointer);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Unable to transform I420 frame");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Unable to transform I420 frame");
        } catch (InstantiationException e) {
            throw new RuntimeException("Unable to transform I420 frame");
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Unable to transform I420 frame");
        }
    }
}
