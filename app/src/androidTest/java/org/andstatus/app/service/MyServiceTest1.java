/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.service;

import android.content.SyncResult;
import android.database.sqlite.SQLiteDiskIOException;

import org.andstatus.app.account.DemoAccountInserter;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.os.ExceptionsCounter;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;
import org.junit.Test;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MyServiceTest1 extends MyServiceTest {

    @Test
    public void testAccountSync() {
        final String method = "testAccountSync";
        MyLog.i(this, method + " started");

        MyAccount myAccount = MyContextHolder.get().accounts().getFirstSucceeded();
        assertTrue("No successful account", myAccount.isValidAndSucceeded());

        MyContext myContext = MyContextHolder.get();
        myContext.timelines().filter(false, TriState.FALSE,
                TimelineType.UNKNOWN, Actor.EMPTY, Origin.EMPTY)
                .filter(Timeline::isSyncedAutomatically)
                .filter(Timeline::isTimeToAutoSync)
                .forEach(timeline -> timeline.onSyncEnded(new CommandResult()));
        myContext.timelines().saveChanged();
        SyncResult syncResult = new SyncResult();
        MyServiceCommandsRunner runner = new MyServiceCommandsRunner(myContext);
        runner.setIgnoreServiceAvailability(true);
        runner.autoSyncAccount(myAccount, syncResult);
        DbUtils.waitMs(this, 5000);
        assertEquals("Requests were sent while all timelines just synced " +
                runner.toString() + "; " + mService.getHttp().toString(),
                0, mService.getHttp().getRequestsCounter());

        myContext = MyContextHolder.get();
        Timeline timelineToSync = DemoAccountInserter.getAutomaticallySyncableTimeline(myContext, myAccount);
        timelineToSync.setSyncSucceededDate(0);

        runner = new MyServiceCommandsRunner(myContext);
        runner.setIgnoreServiceAvailability(true);
        syncResult = new SyncResult();
        runner.autoSyncAccount(myAccount, syncResult);
        DbUtils.waitMs(this, 5000);
        assertEquals("Timeline was not synced: " + timelineToSync + "; " +
                runner.toString() + "; " + mService.getHttp().toString(),
                1, mService.getHttp().getRequestsCounter());

        assertTrue("Service stopped", mService.waitForServiceStopped(true));
        MyLog.i(this, method + " ended");
    }

    @Test
    public void testHomeTimeline() {
        final String method = "testHomeTimeline";
        MyLog.i(this, method + " started");

        mService.setListenedCommand(CommandData.newTimelineCommand(
                CommandEnum.GET_TIMELINE, ma, TimelineType.HOME));
        long startCount = mService.executionStartCount;
        long endCount = mService.executionEndCount;

        mService.sendListenedCommand();
        
        mService.assertCommandExecutionStarted("First command", startCount, TriState.TRUE);
        assertTrue("First command ended executing", mService.waitForCommandExecutionEnded(endCount));
        MyLog.i(this, method  + "; " + mService.getHttp().toString());
        assertEquals("connection instance Id", mService.connectionInstanceId, mService.getHttp().getInstanceId());
        assertEquals(mService.getHttp().toString(), 1, mService.getHttp().getRequestsCounter());
        assertTrue("Service stopped", mService.waitForServiceStopped(true));
        MyLog.i(this, method + " ended");
    }

    @Test
    public void testRateLimitStatus() {
        final String method = "testRateLimitStatus";
        MyLog.i(this, method + " started");

        mService.setListenedCommand(CommandData.newAccountCommand(
                CommandEnum.RATE_LIMIT_STATUS,
                demoData.getMyAccount(demoData.gnusocialTestAccountName)));
        long startCount = mService.executionStartCount;
        long endCount = mService.executionEndCount;

        mService.sendListenedCommand();
        mService.assertCommandExecutionStarted("First command", startCount, TriState.TRUE);
        assertTrue("First command ended executing", mService.waitForCommandExecutionEnded(endCount));
        assertTrue(mService.getHttp().toString(),
                mService.getHttp().getRequestsCounter() > 0);
        assertTrue("Service stopped", mService.waitForServiceStopped(true));
        assertEquals("DiskIoException", 0, ExceptionsCounter.getDiskIoExceptionsCount());
        MyLog.i(this, method + " ended");
    }

    @Test
    public void testDiskIoErrorCatching() throws InterruptedException {
        final String method = "testDiskIoErrorCatching";
        MyLog.i(this, method + " started");

        mService.setListenedCommand(CommandData.newAccountCommand(
                CommandEnum.RATE_LIMIT_STATUS,
                demoData.getMyAccount(demoData.gnusocialTestAccountName)));
        mService.getHttp().setRuntimeException(new SQLiteDiskIOException(method));
        long startCount = mService.executionStartCount;
        mService.sendListenedCommand();

        mService.assertCommandExecutionStarted("First command", startCount, TriState.TRUE);
        assertTrue("Service stopped", mService.waitForServiceStopped(true));
        assertEquals("No DiskIoException", 1, ExceptionsCounter.getDiskIoExceptionsCount());
        DbUtils.waitMs(method, 3000);
        MyLog.i(this, method + " ended");
    }
}
