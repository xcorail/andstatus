/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.note;

import android.content.Context;
import android.support.annotation.NonNull;

import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.actor.ActorListLoader;
import org.andstatus.app.actor.ActorViewItem;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.AttachedImageFile;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.DuplicationLink;
import org.andstatus.app.timeline.TimelineFilter;
import org.andstatus.app.timeline.ViewItem;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.andstatus.app.timeline.DuplicationLink.DUPLICATES;
import static org.andstatus.app.timeline.DuplicationLink.IS_DUPLICATED;
import static org.andstatus.app.util.RelativeTime.SOME_TIME_AGO;

public abstract class BaseNoteViewItem<T extends BaseNoteViewItem<T>> extends ViewItem<T> {
    private static final int MIN_LENGTH_TO_COMPARE = 5;
    MyContext myContext = MyContextHolder.get();
    long updatedDate = 0;
    long activityUpdatedDate = 0;

    public DownloadStatus noteStatus = DownloadStatus.UNKNOWN;

    private long noteId;
    private Origin origin = Origin.EMPTY;

    protected ActorViewItem author = ActorViewItem.EMPTY;

    String recipientName = "";

    public long inReplyToNoteId = 0;
    ActorViewItem inReplyToActor = ActorViewItem.EMPTY;

    String noteSource = "";

    private String name = "";
    private String content = "";
    String contentToSearch = "";

    boolean favorited = false;
    Map<Long, String> rebloggers = new HashMap<>();
    boolean reblogged = false;

    AttachedImageFile attachedImageFile = AttachedImageFile.EMPTY;

    private MyAccount linkedMyAccount = MyAccount.EMPTY;
    public final StringBuilder detailsSuffix = new StringBuilder();

    protected BaseNoteViewItem(boolean isEmpty) {
        super(isEmpty);
    }

    @NonNull
    public MyContext getMyContext() {
        return myContext;
    }

    public void setMyContext(MyContext myContext) {
        this.myContext = myContext;
    }

    public long getNoteId() {
        return noteId;
    }

    void setNoteId(long noteId) {
        this.noteId = noteId;
    }

    public Origin getOrigin() {
        return origin;
    }

    void setOrigin(@NonNull Origin origin) {
        this.origin = origin;
    }

    void setLinkedAccount(long linkedActorId) {
        linkedMyAccount = getMyContext().accounts().fromActorId(linkedActorId);
    }

    @NonNull
    public MyAccount getLinkedMyAccount() {
        return linkedMyAccount;
    }

    private void setCollapsedStatus(StringBuilder noteDetails) {
        if (isCollapsed()) {
            MyStringBuilder.appendWithSpace(noteDetails, "(+" + getChildrenCount() + ")");
        }
    }

    @Override
    @NonNull
    public DuplicationLink duplicates(Timeline timeline, @NonNull T other) {
        if (isEmpty() || other.isEmpty()) return DuplicationLink.NONE;
        return (getNoteId() == other.getNoteId())
                ? duplicatesByFavoritedAndReblogged(timeline, other)
                : duplicatesByOther(timeline, other);
    }

    @NonNull
    private DuplicationLink duplicatesByFavoritedAndReblogged(Timeline timeline, @NonNull T other) {
        if (favorited != other.favorited) {
            return favorited ? IS_DUPLICATED : DUPLICATES;
        } else if (reblogged != other.reblogged) {
            return reblogged ? IS_DUPLICATED : DUPLICATES;
        }
        if (timeline.preferredOrigin().nonEmpty()
                && !author.getActor().origin.equals(other.author.getActor().origin)) {
            if (timeline.preferredOrigin().equals(author.getActor().origin)) return IS_DUPLICATED;
            if (timeline.preferredOrigin().equals(other.author.getActor().origin)) return DUPLICATES;
        }
        if (!getLinkedMyAccount().equals(other.getLinkedMyAccount())) {
            return getLinkedMyAccount().compareTo(other.getLinkedMyAccount()) <= 0
                    ? IS_DUPLICATED : DUPLICATES;
        }
        return rebloggers.size() > other.rebloggers.size() ? IS_DUPLICATED : DUPLICATES;
    }

    @NonNull
    private DuplicationLink duplicatesByOther(Timeline timeline, @NonNull T other) {
        if (updatedDate != SOME_TIME_AGO && other.updatedDate != SOME_TIME_AGO
              &&  (Math.abs(updatedDate - other.updatedDate) >= TimeUnit.HOURS.toMillis(24))
                || isTooShortToCompare()
                || other.isTooShortToCompare()
                ) return DuplicationLink.NONE;
        if (contentToSearch.equals(other.contentToSearch)) {
            if (updatedDate == other.updatedDate) {
                return duplicatesByFavoritedAndReblogged(timeline, other);
            } else if (updatedDate < other.updatedDate) {
                return IS_DUPLICATED;
            } else {
                return DUPLICATES;
            }
        } else if (contentToSearch.contains(other.contentToSearch)) {
            return DUPLICATES;
        } else if (other.contentToSearch.contains(contentToSearch)) {
            return IS_DUPLICATED;
        }
        return DuplicationLink.NONE;
    }

    boolean isTooShortToCompare() {
        return contentToSearch.length() < MIN_LENGTH_TO_COMPARE;
    }

    public boolean isReblogged() {
        return !rebloggers.isEmpty();
    }

    public StringBuilder getDetails(Context context) {
        StringBuilder builder = new StringBuilder(RelativeTime.getDifference(context, updatedDate));
        setInReplyTo(context, builder);
        setRecipientName(context, builder);
        setNoteSource(context, builder);
        setAccountDownloaded(builder);
        setNoteStatus(context, builder);
        setCollapsedStatus(builder);
        if (detailsSuffix.length() > 0) MyStringBuilder.appendWithSpace(builder, detailsSuffix.toString());
        return builder;
    }

    protected void setInReplyTo(Context context, StringBuilder noteDetails) {
        if (inReplyToNoteId == 0 || inReplyToActor.isEmpty()) return;

        MyStringBuilder.appendWithSpace(noteDetails, String.format(context.getText(R.string.message_source_in_reply_to).toString(),
                inReplyToActor.getName()));
    }

    private void setRecipientName(Context context, StringBuilder noteDetails) {
        if (!StringUtils.isEmpty(recipientName)) {
            noteDetails.append(" " + String.format(
                    context.getText(R.string.message_source_to).toString(),
                    recipientName));
        }
    }

    private void setNoteSource(Context context, StringBuilder noteDetails) {
        if (!SharedPreferencesUtil.isEmpty(noteSource) && !"ostatus".equals(noteSource)
                && !"unknown".equals(noteSource)) {
            noteDetails.append(" " + String.format(
                    context.getText(R.string.message_source_from).toString(), noteSource));
        }
    }

    private void setAccountDownloaded(StringBuilder noteDetails) {
        if (MyPreferences.isShowMyAccountWhichDownloadedActivity() && linkedMyAccount.isValid()) {
            noteDetails.append(" a:" + linkedMyAccount.getShortestUniqueAccountName(myContext));
        }
    }

    private void setNoteStatus(Context context, StringBuilder noteDetails) {
        if (noteStatus != DownloadStatus.LOADED) {
            noteDetails.append(" (").append(noteStatus.getTitle(context)).append(")");
        }
    }

    public AttachedImageFile getAttachedImageFile() {
        return attachedImageFile;
    }

    public BaseNoteViewItem setName(String name) {
        this.name = name;
        return this;
    }

    public String getName() {
        return name;
    }

    public BaseNoteViewItem setContent(String content) {
        this.content = content;
        return this;
    }

    public String getContent() {
        return content;
    }

    @Override
    public long getId() {
        return getNoteId();
    }

    @Override
    public long getDate() {
        return activityUpdatedDate;
    }

    @Override
    public boolean matches(TimelineFilter filter) {
        if (filter.keywordsFilter.nonEmpty() || filter.searchQuery.nonEmpty()) {
            if (filter.keywordsFilter.matchedAny(contentToSearch)) return false;

            if (filter.searchQuery.nonEmpty() && !filter.searchQuery.matchedAll(contentToSearch)) return false;
        }
        return !filter.hideRepliesNotToMeOrFriends
                || inReplyToActor.isEmpty()
                || MyContextHolder.get().users().isMeOrMyFriend(inReplyToActor.getActor());
    }

    @Override
    public String toString() {
        return "Note " + content;
    }

    public void hideActor(Actor actor) {
        rebloggers.remove(actor.actorId);
    }

    @Override
    public void addActorsToLoad(ActorListLoader loader) {
        loader.addActorToList(author.getActor());
        loader.addActorToList(inReplyToActor.getActor());
    }

    @Override
    public void setLoadedActors(ActorListLoader loader) {
        if (author.getActor().nonEmpty()) {
            int index = loader.getList().indexOf(author);
            if (index >= 0) {
                author = loader.getList().get(index);
            }
        }
        if (inReplyToActor.getActor().nonEmpty()) {
            int index = loader.getList().indexOf(inReplyToActor);
            if (index >= 0) {
                inReplyToActor = loader.getList().get(index);
            }
        }
    }
}
