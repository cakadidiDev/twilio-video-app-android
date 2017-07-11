/*
 * Copyright (C) 2017 Twilio, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "com_twilio_video_LocalParticipant.h"
#include "com_twilio_video_LocalAudioTrack.h"
#include "com_twilio_video_LocalVideoTrack.h"

namespace twilio_video_jni {

std::shared_ptr<twilio::video::LocalParticipant> getLocalParticipant(jlong local_participant_handle) {
    LocalParticipantContext* local_participant_context =
        reinterpret_cast<LocalParticipantContext *>(local_participant_handle);
    return local_participant_context->getLocalParticipant();
}

JNIEXPORT bool JNICALL Java_com_twilio_video_LocalParticipant_nativeAddAudioTrack(JNIEnv *jni,
                                                                                  jobject j_local_participant,
                                                                                  jlong j_local_participant_handle,
                                                                                  jlong j_audio_track_handle) {
    auto local_participant = getLocalParticipant(j_local_participant_handle);
    auto audio_track = getLocalAudioTrack(j_audio_track_handle);
    return local_participant->addTrack(audio_track);
}

JNIEXPORT bool JNICALL Java_com_twilio_video_LocalParticipant_nativeAddVideoTrack(JNIEnv *jni,
                                                                                  jobject j_local_participant,
                                                                                  jlong j_local_participant_handle,
                                                                                  jlong j_video_track_handle) {
    auto local_participant = getLocalParticipant(j_local_participant_handle);
    auto video_track = getLocalVideoTrack(j_video_track_handle);
    return local_participant->addTrack(video_track);
}

JNIEXPORT bool JNICALL Java_com_twilio_video_LocalParticipant_nativeRemoveAudioTrack(JNIEnv *jni,
                                                                                     jobject j_local_participant,
                                                                                     jlong j_local_participant_handle,
                                                                                     jlong j_audio_track_handle) {
    auto local_participant = getLocalParticipant(j_local_participant_handle);
    auto audio_track = getLocalAudioTrack(j_audio_track_handle);
    return local_participant->removeTrack(audio_track);
}

JNIEXPORT bool JNICALL Java_com_twilio_video_LocalParticipant_nativeRemoveVideoTrack(JNIEnv *jni,
                                                                                     jobject j_local_participant,
                                                                                     jlong j_local_participant_handle,
                                                                                     jlong j_video_track_handle) {
    auto local_participant = getLocalParticipant(j_local_participant_handle);
    auto video_track = getLocalVideoTrack(j_video_track_handle);
    return local_participant->removeTrack(video_track);
}

JNIEXPORT void JNICALL Java_com_twilio_video_LocalParticipant_nativeRelease(JNIEnv *jni,
                                                                            jobject j_local_participant,
                                                                            jlong j_local_participant_handle) {
    LocalParticipantContext* local_participant_context =
        reinterpret_cast<LocalParticipantContext *>(j_local_participant_handle);
    delete local_participant_context;
}



}
