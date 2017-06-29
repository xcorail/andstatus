/*
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.context;

import android.app.Activity;
import android.content.Intent;
import android.support.annotation.NonNull;

import org.andstatus.app.HelpActivity;
import org.andstatus.app.net.http.TlsSniSocketFactory;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.ExceptionsCounter;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.util.concurrent.Executor;

/**
 * @author yvolk@yurivolkov.com
 */
public class MyFutureContext extends MyAsyncTask<Void, Void, MyContext> {
    @NonNull
    private final MyContext contextCreator;
    private volatile MyContext myPreviousContext = null;
    private final String callerName;

    private volatile Intent activityIntentPostRun = null;
    private volatile Runnable runnablePostRun = null;

    private static class DirectExecutor implements Executor {
        private DirectExecutor() {
        }

        public void execute(@NonNull Runnable command) {
            command.run();
        }
    }

    MyFutureContext(@NonNull MyContext contextCreator, MyContext myPreviousContext, @NonNull Object calledBy) {
        super(MyFutureContext.class.getSimpleName(), PoolEnum.QUICK_UI);
        this.contextCreator = contextCreator;
        this.myPreviousContext = myPreviousContext;
        callerName = MyLog.objTagToString(calledBy);
    }

    void executeOnNonUiThread() {
        if (isUiThread()) {
            execute();
        } else {
            executeOnExecutor(new DirectExecutor());
        }
    }

    @Override
    protected MyContext doInBackground2(Void... params) {
        MyLog.d(this, "Starting initialization by " + callerName);
        releaseGlobal();
        return contextCreator.newInitialized(callerName);
    }

    private void releaseGlobal() {
        TlsSniSocketFactory.forget();
        AsyncTaskLauncher.forget();
        ExceptionsCounter.forget();
        MyLog.forget();
        SharedPreferencesUtil.forget();
        MyLog.d(this, "releaseGlobal completed");
    }

    @Override
    protected void onPostExecute(MyContext myContext) {
        onPostExecute2(myContext);
        super.onPostExecute(myContext);
    }

    private void onPostExecute2(MyContext myContext) {
        runRunnable(myContext);
        startActivity(myContext);
    }

    public boolean isEmpty() {
        return false;
    }

    public void thenStartActivity(Activity activity) {
        if (activity != null) {
            thenStartActivity(activity.getIntent());
        }
    }

    public void thenStartActivity(Intent intent) {
        activityIntentPostRun = intent;
        if (getStatus() == Status.FINISHED) {
            startActivity(getNow());
        }
    }

    private void startActivity(MyContext myContext) {
        Intent intent = activityIntentPostRun;
        if (intent != null) {
            runnablePostRun = null;
            if (myContext.isReady()) {
                myContext.context().startActivity(intent);
            } else {
                HelpActivity.startMe(myContext.context(), true, false);
            }
            activityIntentPostRun = null;
        }
    }

    public void thenRun(Runnable runnable) {
        this.runnablePostRun = runnable;
        if (getStatus() == Status.FINISHED) {
            runRunnable(getNow());
        }
    }

    private void runRunnable(MyContext myContext) {
        if (runnablePostRun != null) {
            if (myContext.isReady()) {
                runnablePostRun.run();
            } else {
                HelpActivity.startMe(myContext.context(), true, false);
            }
            runnablePostRun = null;
        }
    }

    /**
     * Immediately get currently available context, even if it's empty
     */
    @NonNull
    public MyContext getNow() {
        if (getStatus() == Status.FINISHED) {
            return getBlocking();
        }
        return getMyContext();
    }

    @NonNull
    private MyContext getMyContext() {
        if(myPreviousContext == null) {
            return contextCreator;
        }
        return myPreviousContext;
    }

    @NonNull
    public MyContext getBlocking() {
        try {
            MyContext myContext = get();
            myPreviousContext = null;
            return myContext;
        } catch (Exception e) {
            return getMyContext();
        }
    }

}