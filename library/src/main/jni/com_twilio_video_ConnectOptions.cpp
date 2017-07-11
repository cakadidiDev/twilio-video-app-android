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

#include "com_twilio_video_ConnectOptions.h"
#include "com_twilio_video_IceOptions.h"
#include "com_twilio_video_PlatformInfo.h"
#include "com_twilio_video_LocalAudioTrack.h"
#include "com_twilio_video_LocalVideoTrack.h"

#include "webrtc/sdk/android/src/jni/jni_helpers.h"
#include "class_reference_holder.h"

namespace twilio_video_jni {

JNIEXPORT jlong JNICALL
Java_com_twilio_video_ConnectOptions_nativeCreate(JNIEnv *env,
                                                  jobject j_instance,
                                                  jstring j_access_token,
                                                  jstring j_room_name,
                                                  jobjectArray j_audio_tracks,
                                                  jobjectArray j_video_tracks,
                                                  jobject j_ice_options,
                                                  jboolean j_enable_insights,
                                                  jlong j_platform_info_handle) {

    std::string access_token = webrtc_jni::JavaToStdString(env, j_access_token);
    twilio::video::ConnectOptions::Builder* builder =
        new twilio::video::ConnectOptions::Builder(access_token);

    if (!webrtc_jni::IsNull(env, j_room_name)) {
        std::string name = webrtc_jni::JavaToStdString(env, j_room_name);
        builder->setRoomName(name);
    }

    if (!webrtc_jni::IsNull(env, j_audio_tracks)) {
        jclass j_local_audio_track_class =
            twilio_video_jni::FindClass(env, "com/twilio/video/LocalAudioTrack");
        jmethodID j_local_audio_track_get_native_handle =
            webrtc_jni::GetMethodID(env, j_local_audio_track_class, "getNativeHandle", "()J");

        std::vector<std::shared_ptr<twilio::media::LocalAudioTrack>> audio_tracks;
        int size = env->GetArrayLength(j_audio_tracks);
        if (size > 0) {
            for (int i = 0; i < size; i++) {
                // Get local audio track handle
                jobject j_audio_track = (jobject) env->GetObjectArrayElement(j_audio_tracks, i);
                jlong j_audio_track_handle =
                    (jlong) env->CallLongMethod(j_audio_track,
                                                j_local_audio_track_get_native_handle);
                // Get local audio track
                auto audio_track = getLocalAudioTrack(j_audio_track_handle);
                audio_tracks.push_back(audio_track);
            }
            builder->setAudioTracks(audio_tracks);
        }
    }

    if(!webrtc_jni::IsNull(env, j_video_tracks)) {
        jclass j_local_video_track_class =
            twilio_video_jni::FindClass(env, "com/twilio/video/LocalVideoTrack");
        jmethodID j_local_video_track_get_native_handle =
            webrtc_jni::GetMethodID(env, j_local_video_track_class, "getNativeHandle", "()J");

        std::vector<std::shared_ptr<twilio::media::LocalVideoTrack>> video_tracks;
        int size = env->GetArrayLength(j_video_tracks);
        if (size > 0) {
            for (int i = 0; i < size; i++) {
                // Get local video track handle
                jobject j_video_track = (jobject) env->GetObjectArrayElement(j_video_tracks, i);
                jlong j_video_track_handle =
                    (jlong) env->CallLongMethod(j_video_track,
                                                j_local_video_track_get_native_handle);
                // Get local video track
                auto video_track = getLocalVideoTrack(j_video_track_handle);
                video_tracks.push_back(video_track);
            }
            builder->setVideoTracks(video_tracks);
        }
    }

    if (!webrtc_jni::IsNull(env, j_ice_options)) {
        twilio::media::IceOptions ice_options = IceOptions::getIceOptions(env, j_ice_options);
        builder->setIceOptions(ice_options);
    }

    PlatformInfoContext *platform_info_context =
        reinterpret_cast<PlatformInfoContext *>(j_platform_info_handle);
    if (platform_info_context != nullptr) {
        builder->setPlatformInfo(platform_info_context->platform_info);
    }

    builder->enableInsights(j_enable_insights);

    return webrtc_jni::jlongFromPointer(builder);
}

}
