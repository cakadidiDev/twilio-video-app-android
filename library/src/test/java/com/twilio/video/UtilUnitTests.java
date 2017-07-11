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

package com.twilio.video;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class UtilUnitTests {
    @Mock Context mockContext;

    @Test
    public void permissionGranted() {
        String grantedPermission = Manifest.permission.CAMERA;
        String deniedPermission = Manifest.permission.RECORD_AUDIO;

        when(mockContext.checkCallingOrSelfPermission(grantedPermission))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mockContext.checkCallingOrSelfPermission(deniedPermission))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        assertTrue(Util.permissionGranted(mockContext, grantedPermission));
        assertFalse(Util.permissionGranted(mockContext, deniedPermission));
    }
}
