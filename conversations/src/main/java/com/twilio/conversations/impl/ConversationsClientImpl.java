package com.twilio.conversations.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;

import com.twilio.common.TwilioAccessManager;
import com.twilio.conversations.AudioOutput;
import com.twilio.conversations.Conversation;
import com.twilio.conversations.ConversationCallback;
import com.twilio.conversations.ConversationListener;
import com.twilio.conversations.ConversationsClient;
import com.twilio.conversations.ConversationsClientListener;
import com.twilio.conversations.InviteStatus;
import com.twilio.conversations.LocalMedia;
import com.twilio.conversations.OutgoingInvite;
import com.twilio.conversations.Participant;
import com.twilio.conversations.TwilioConversations;
import com.twilio.conversations.TwilioConversationsException;
import com.twilio.conversations.impl.core.ConversationStateObserver;
import com.twilio.conversations.impl.core.ConversationStatus;
import com.twilio.conversations.impl.core.CoreEndpoint;
import com.twilio.conversations.impl.core.CoreError;
import com.twilio.conversations.impl.core.EndpointObserver;
import com.twilio.conversations.impl.core.EndpointState;
import com.twilio.conversations.impl.core.SessionState;
import com.twilio.conversations.impl.logging.Logger;
import com.twilio.conversations.impl.util.CallbackHandler;

public class ConversationsClientImpl implements
        ConversationsClient,
        NativeHandleInterface,
        Parcelable,
        EndpointObserver,
        CoreEndpoint, ConversationListener, ConversationStateObserver {
    static final Logger logger = Logger.getLogger(ConversationsClientImpl.class);

    // Current support is limited to one conversation
    private static final String DISPOSE_MESSAGE = "The ConversationsClient has been disposed. " +
            "This operation is no longer valid";
    private static final String FINALIZE_MESSAGE = "The ConversationsClient must be released " +
            "by calling dispose(). Failure to do so may result in leaked resources.";

    void removeConversation(ConversationImpl conversationImpl) {
        conversations.remove(conversationImpl);
    }

    void onConversationTerminated(ConversationImpl conversationImpl,
                                  TwilioConversationsException e) {
        conversations.remove(conversationImpl);
        IncomingInviteImpl incomingInvite = pendingIncomingInvites
                .remove(conversationImpl.getIncomingInviteImpl());
        conversationImpl.getIncomingInviteImpl().setStatus(InviteStatus.CANCELLED);
        conversationsClientListener.onIncomingInviteCancelled(this,
                conversationImpl.getIncomingInviteImpl());
    }

    class EndpointObserverInternal implements NativeHandleInterface {

        private long nativeEndpointObserver;

        public EndpointObserverInternal(EndpointObserver observer) {
            this.nativeEndpointObserver = wrapNativeObserver(observer,
                    ConversationsClientImpl.this);
        }

        private native long wrapNativeObserver(EndpointObserver observer,
                                               ConversationsClient conversationsClient);
        private native void freeNativeObserver(long nativeEndpointObserver);

        @Override
        public long getNativeHandle() {
            return nativeEndpointObserver;
        }

        public void dispose() {
            if (nativeEndpointObserver != 0) {
                freeNativeObserver(nativeEndpointObserver);
                nativeEndpointObserver = 0;
            }
        }

    }

    private DisposalState disposalState = DisposalState.NOT_DISPOSED;

    private final UUID uuid = UUID.randomUUID();
    private Context context;
    private ConversationsClientListener conversationsClientListener;
    private EndpointObserverInternal endpointObserver;
    private long nativeEndpointHandle;
    private boolean listening = false;
    private TwilioAccessManager accessManager;
    private Handler handler;
    private EndpointState endpointState;
    private Set<ConversationImpl> conversations = Collections
            .newSetFromMap(new ConcurrentHashMap<ConversationImpl, Boolean>());
    private Map<ConversationImpl, OutgoingInviteImpl> pendingOutgoingInvites = new HashMap<>();
    private Map<ConversationImpl, IncomingInviteImpl> pendingIncomingInvites = new HashMap<>();

    public UUID getUuid() {
        return uuid;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    protected void finalize() throws Throwable {
        if (disposalState == DisposalState.NOT_DISPOSED || nativeEndpointHandle != 0) {
            logger.e(FINALIZE_MESSAGE);
            dispose();
        }
    }


    ConversationsClientImpl(Context context,
                            TwilioAccessManager accessManager,
                            ConversationsClientListener conversationsClientListener) {
        this.context = context;
        this.conversationsClientListener = conversationsClientListener;
        this.accessManager = accessManager;

        this.endpointObserver = new EndpointObserverInternal(this);

        handler = CallbackHandler.create();
        if(handler == null) {
            throw new IllegalThreadStateException("This thread must be able to obtain a Looper");
        }
    }

    void setNativeEndpointHandle(long nativeEndpointHandle) {
        this.nativeEndpointHandle = nativeEndpointHandle;
    }

    long getEndpointObserverHandle() {
        return this.endpointObserver.getNativeHandle();
    }

    int getActiveConversationsCount() {
        int activeConversations = 0;
        for (ConversationImpl conv : conversations) {
            if (conv.isActive()) {
                activeConversations++;
            }
        }
        return activeConversations;
    }

    @Override
    public void listen() {
        checkDisposed();
        listen(nativeEndpointHandle);
    }

    @Override
    public void unlisten() {
        checkDisposed();
        if(listening) {
            unlisten(nativeEndpointHandle);
            listening = false;
        }
    }

    @Override
    public void setConversationsClientListener(ConversationsClientListener listener) {
        this.conversationsClientListener = listener;
    }

    @Override
    public String getIdentity() {
        return new String(accessManager.getIdentity());
    }

    @Override
    public boolean isListening() {
        return listening;
    }

    @Override
    public OutgoingInvite sendConversationInvite(Set<String> participants,
                                                 LocalMedia localMedia,
                                                 ConversationCallback conversationCallback) {
        checkDisposed();
        if(participants == null || participants.size() == 0) {
            throw new IllegalStateException("Invite at least one participant");
        }
        if(localMedia == null) {
            throw new IllegalStateException("Local media is required to create a conversation");
        }
        if(conversationCallback == null) {
            throw new IllegalStateException("A ConversationCallback is required to retrieve the conversation");
        }
        for (String participant : participants) {
            if (participant == null || participant.isEmpty() ) {
                throw new IllegalArgumentException("Participant cannot be an empty string");
            }
        }

        ConversationImpl outgoingConversationImpl = ConversationImpl.createOutgoingConversation(
                this, participants, localMedia, this, this);

        if (outgoingConversationImpl == null ||
                outgoingConversationImpl.getNativeHandle() == 0) {
            TwilioConversationsException exception =
                    new TwilioConversationsException(TwilioConversations.CLIENT_DISCONNECTED,
                            "Cannot create conversation while reconnecting. " +
                                    "Wait for conversations client to reconnect and try again.");
            conversationCallback.onConversation(null, exception);
            return null;
        } else {
            outgoingConversationImpl.start();
        }

        conversations.add(outgoingConversationImpl);
        logger.i("Conversations size is now " + conversations.size());

        OutgoingInviteImpl outgoingInviteImpl = OutgoingInviteImpl.create(this, outgoingConversationImpl, conversationCallback);
        pendingOutgoingInvites.put(outgoingConversationImpl, outgoingInviteImpl);
        outgoingConversationImpl.setOutgoingInviteImpl(outgoingInviteImpl);

        return outgoingInviteImpl;
    }

    @Override
    public void onConversationStatusChanged(Conversation conversation,
                                            ConversationStatus conversationStatus) {
        ConversationImpl conversationImpl = (ConversationImpl)conversation;
        if(conversationStatus.equals(ConversationStatus.CONNECTED) &&
                conversationImpl.getSessionState().equals(SessionState.IN_PROGRESS)) {
            handleConversationStarted(conversationImpl);
        }
    }

    private void handleConversationStarted(final ConversationImpl conversationImpl) {
        final IncomingInviteImpl incomingInviteImpl = pendingIncomingInvites.get(conversationImpl);
        if(incomingInviteImpl != null) {
            incomingInviteImpl.setStatus(InviteStatus.ACCEPTED);
            // Remove the invite since it has reached its final state
            pendingIncomingInvites.remove(conversationImpl);
            // Stop listening to ConversationListener. The developer should provide their own listener
            conversationImpl.setConversationListener(null);
            // Notify the developer that the conversation is active
            if (incomingInviteImpl.getHandler() != null && incomingInviteImpl.getConversationCallback() != null) {
                /**
                 * Block the thread to ensure no other callbacks are called until the developer
                 * handles this callback.
                 */
                final CountDownLatch waitLatch = new CountDownLatch(1);
                incomingInviteImpl.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        incomingInviteImpl.getConversationCallback().onConversation(conversationImpl, null);
                        waitLatch.countDown();
                    }
                });
                try {
                    waitLatch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        final OutgoingInviteImpl outgoingInviteImpl = pendingOutgoingInvites.get(conversationImpl);
        if(outgoingInviteImpl != null) {
            outgoingInviteImpl.setStatus(InviteStatus.ACCEPTED);
            // Remove the invite since it has reached its final state
            pendingOutgoingInvites.remove(conversationImpl);
            // Stop listening to ConversationListener. The developer should provide their own listener
            conversationImpl.setConversationListener(null);
            if (outgoingInviteImpl.getHandler() != null && outgoingInviteImpl.getConversationCallback() != null) {
                /**
                 * Block the thread to ensure no other callbacks are called until the developer handles
                 * this callback.
                 */
                final CountDownLatch waitLatch = new CountDownLatch(1);
                outgoingInviteImpl.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        outgoingInviteImpl.getConversationCallback().onConversation(conversationImpl, null);
                        waitLatch.countDown();
                    }
                });
                try {
                    waitLatch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private void handleConversationFailed(final ConversationImpl conversationImpl,
                                          final TwilioConversationsException e) {
        final OutgoingInviteImpl outgoingInviteImpl = pendingOutgoingInvites.get(conversationImpl);
        if(outgoingInviteImpl != null) {
            InviteStatus status = outgoingInviteImpl.getStatus() == InviteStatus.CANCELLED ?
                    InviteStatus.CANCELLED :
                    InviteStatus.FAILED;
            outgoingInviteImpl.setStatus(status);
            pendingOutgoingInvites.remove(conversationImpl);
            if (outgoingInviteImpl.getHandler() != null &&
                    outgoingInviteImpl.getConversationCallback() != null) {
                outgoingInviteImpl.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        // The call ended by the user
                        if (conversationImpl != null) {
                            conversationImpl.dispose();
                        }
                        if(e != null) {
                            outgoingInviteImpl.getConversationCallback()
                                    .onConversation(conversationImpl, e);
                        } else {
                            outgoingInviteImpl.getConversationCallback()
                                    .onConversation(conversationImpl, e);
                            if (conversationImpl != null) {
                                conversationImpl.getConversationListener()
                                        .onConversationEnded(conversationImpl, null);
                            }
                        }
                    }
                });
            }
        }

        conversations.remove(conversationImpl);
    }

    @Override
    public void onParticipantConnected(Conversation conversation, Participant participant) {
        logger.w("Not expecting a connected participant " + participant + " while inviting.");
    }

    @Override
    public void onFailedToConnectParticipant(Conversation conversation, Participant participant, TwilioConversationsException e) {
        ConversationImpl conversationImpl = (ConversationImpl)conversation;
        handleConversationStarting(conversationImpl);
    }

    private void handleConversationStarting(ConversationImpl conversationImpl) {
        // Do nothing.
    }

    @Override
    public void onParticipantDisconnected(Conversation conversation, Participant participant) {
        ConversationImpl conversationImpl = (ConversationImpl)conversation;
    }

    @Override
    public void onConversationEnded(Conversation conversation, TwilioConversationsException e) {
        ConversationImpl conversationImpl = (ConversationImpl)conversation;
        handleConversationFailed(conversationImpl, e);
    }

    @Override
    public synchronized void dispose() {
        disposalState = DisposalState.DISPOSING;
        if (listening) {
            // The client must stop listening before the ConversationsClient can be disposed
            unlisten();
        } else {
            disposeClient();
        }
    }

    @Override /* Parcelable */
    public int describeContents() {
        return 0;
    }

    @Override /* Parcelable */
    public void writeToParcel(Parcel out, int flags) {
        out.writeSerializable(uuid);
    }

    /* Parcelable */
    public static final Parcelable.Creator<ConversationsClientImpl> CREATOR = new Parcelable.Creator<ConversationsClientImpl>() {
        @Override
        public ConversationsClientImpl createFromParcel(Parcel in) {
            UUID uuid = (UUID)in.readSerializable();
            TwilioConversationsImpl twilioConversations = TwilioConversationsImpl.getInstance();
            return twilioConversations.findDeviceByUUID(uuid);
        }

        @Override
        public ConversationsClientImpl[] newArray(int size)
        {
            throw new UnsupportedOperationException();
        }
    };

    /**
     * NativeHandleInterface
     */
    @Override
    public long getNativeHandle() {
        return nativeEndpointHandle;
    }

    /**
     * EndpointObserver methods
     */
    @Override
    public void onRegistrationDidComplete(CoreError error) {
        logger.d("onRegistrationDidComplete");
        if (error != null) {
            listening = false;
            final TwilioConversationsException e =
                    new TwilioConversationsException(error.getCode(), error.getMessage());
            if (handler != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        conversationsClientListener.onFailedToStartListening(ConversationsClientImpl.this, e);
                    }
                });
            }
        } else {
            listening = true;
            if (handler != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        conversationsClientListener.onStartListeningForInvites(ConversationsClientImpl.this);
                    }
                });
            }
        }
    }

    @Override
    public void onUnregistrationDidComplete(CoreError error) {
        logger.d("onUnregistrationDidComplete");
        listening = false;
        if (disposalState == DisposalState.DISPOSING) {
            // If the client was listening, dispose() will call unlisten() to properly dispose of the client
            disposeClient();
        }
        if (handler != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    conversationsClientListener.onStopListeningForInvites(ConversationsClientImpl.this);
                }
            });
        }

    }

    @Override
    public void onStateDidChange(EndpointState state) {
        logger.d("onStateDidChange " + state.toString());
        EndpointState oldEndpointState = endpointState;
        endpointState = state;
        if ((oldEndpointState == EndpointState.RECONNECTING) && (endpointState == EndpointState.REGISTERED)) {
            if (handler != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        conversationsClientListener.onStartListeningForInvites(ConversationsClientImpl.this);
                    }
                });
            }

        } else if (endpointState == EndpointState.RECONNECTING) {
            if (handler != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        conversationsClientListener.onStopListeningForInvites(ConversationsClientImpl.this);
                    }
                });
            }
        }
    }

    @Override
    public void onIncomingCallDidReceive(long nativeSession, String[] participants) {
        logger.d("onIncomingCallDidReceive");

        ConversationImpl incomingConversationImpl =
                ConversationImpl.createIncomingConversation(this,
                        nativeSession,
                        participants,
                        this);
        if (incomingConversationImpl == null) {
            logger.e("Failed to create conversation");
            return;
        }

        conversations.add(incomingConversationImpl);

        final IncomingInviteImpl incomingInviteImpl = IncomingInviteImpl.create(this,
                incomingConversationImpl);
        if (incomingInviteImpl == null) {
            logger.e("Failed to create IncomingInvite");
            return;
        }
        incomingConversationImpl.setIncomingInviteImpl(incomingInviteImpl);

        pendingIncomingInvites.put(incomingConversationImpl, incomingInviteImpl);

        if (handler != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    conversationsClientListener.onIncomingInvite(ConversationsClientImpl.this,
                            incomingInviteImpl);
                }
            });
        }
    }

	/*
	 * CoreEndpoint methods
	 */


    /*
     * Accept the incoming invite.
     */
    @Override
    public void accept(ConversationImpl conversationImpl) {
        conversationImpl.start();
    }

    /*
     * Rejects the incoming invite. This removes the pending conversation and the invite.
     */
    @Override
    public void reject(ConversationImpl conversationImpl) {
        reject(getNativeHandle(), conversationImpl.getNativeHandle());
        pendingIncomingInvites.remove(conversationImpl);
        conversations.remove(conversationImpl);
    }

    @Override
    public void ignore(ConversationImpl conversationImpl) {
        // We are intentionally not implementing ignore
    }

    @Override
    public void setAudioOutput(AudioOutput audioOutput) {
        logger.d("setAudioOutput");
        AudioManager audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        if (audioOutput == AudioOutput.SPEAKERPHONE) {
            audioManager.setSpeakerphoneOn(true);
        } else {
            audioManager.setSpeakerphoneOn(false);
        }

    }

    @Override
    public AudioOutput getAudioOutput() {
        logger.d("getAudioOutput");
        AudioManager audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        return audioManager.isSpeakerphoneOn() ? AudioOutput.SPEAKERPHONE : AudioOutput.HEADSET;
    }


    public DisposalState getDisposalState() {
        return disposalState;
    }

    private void disposeClient() {
        if (disposalState != DisposalState.DISPOSED || nativeEndpointHandle != 0) {
            freeNativeHandle(nativeEndpointHandle);
            nativeEndpointHandle = 0;
            disposalState = DisposalState.DISPOSED;
        }
        if (endpointObserver != null) {
            endpointObserver.dispose();
            endpointObserver = null;
        }
    }

    private synchronized void checkDisposed() {
        if (disposalState == DisposalState.DISPOSED || nativeEndpointHandle == 0) {
            throw new IllegalStateException(DISPOSE_MESSAGE);
        }
    }

    private native void listen(long nativeEndpoint);
    private native void unlisten(long nativeEndpoint);
    private native void reject(long nativeEndpoint, long nativeSession);
    private native void freeNativeHandle(long nativeEndpoint);
}
