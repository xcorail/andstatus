/*
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.actor;

import android.support.annotation.NonNull;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;

import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.activity.ActivityViewItem;
import org.andstatus.app.note.NoteEditorContainer;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.ContextMenuHeader;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.view.MyContextMenu;

public class ActorContextMenu extends MyContextMenu {
    public final NoteEditorContainer menuContainer;

    public ActorContextMenu(NoteEditorContainer menuContainer, int menuGroup) {
        super(menuContainer.getActivity(), menuGroup);
        this.menuContainer = menuContainer;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        final String method = "onCreateContextMenu";
        super.onCreateContextMenu(menu, v, menuInfo);
        if (getViewItem().isEmpty()) {
            return;
        }
        if (!getMyContext().accounts().succeededForSameUser(getViewItem().actor).contains(getSelectedActingAccount())) {
            setSelectedActingAccount(getMyContext().accounts()
                    .firstOtherSucceededForSameUser(getViewItem().actor, getActingAccount()));
        }

        int order = 0;
        try {
            new ContextMenuHeader(getActivity(), menu)
                    .setTitle(getViewItem().actor.toActorTitle(false))
                    .setSubtitle(getActingAccount().getAccountName());
            String shortName = getViewItem().actor.getUsername();
            if (getViewItem().actor.isIdentified()) {
                ActorContextMenuItem.NOTES_BY_ACTOR.addTo(menu, menuGroup, order++,
                        String.format(getActivity().getText(R.string.menu_item_user_messages).toString(), shortName));
                ActorContextMenuItem.FRIENDS.addTo(menu, menuGroup, order++,
                        String.format(
                                getActivity().getText(R.string.friends_of).toString(), shortName));
                ActorContextMenuItem.FOLLOWERS.addTo(menu, menuGroup, order++,
                        String.format(
                                getActivity().getText(R.string.followers_of).toString(), shortName));

                if (getActingAccount().getActor().notSameUser(getViewItem().getActor())) {
                    if (getActingAccount().isFollowing(getMyContext(), getViewItem().getActor())) {
                        ActorContextMenuItem.STOP_FOLLOWING.addTo(menu, menuGroup, order++,
                                String.format(getActivity().getText(R.string.menu_item_stop_following_user).toString(), shortName));
                    } else {
                        ActorContextMenuItem.FOLLOW.addTo(menu, menuGroup, order++,
                                String.format(getActivity().getText(R.string.menu_item_follow_user).toString(), shortName));
                    }
                }
                if (!menuContainer.getNoteEditor().isVisible()) {
                    // TODO: Only if he follows me?
                    ActorContextMenuItem.PRIVATE_NOTE.addTo(menu, menuGroup, order++,
                            R.string.menu_item_private_message);
                }
                switch (getMyContext().accounts().succeededForSameUser(getViewItem().actor).size()) {
                    case 0:
                    case 1:
                        break;
                    case 2:
                        ActorContextMenuItem.ACT_AS_FIRST_OTHER_ACCOUNT.addTo(menu, menuGroup, order++,
                            String.format(
                                getActivity().getText(R.string.menu_item_act_as_user).toString(),
                                getMyContext().accounts()
                                    .firstOtherSucceededForSameUser(getViewItem().actor, getActingAccount())
                                    .getShortestUniqueAccountName(getMyContext())));
                        break;
                    default:
                        ActorContextMenuItem.ACT_AS.addTo(menu, menuGroup, order++, R.string.menu_item_act_as);
                        break;
                }

            }
            ActorContextMenuItem.GET_ACTOR.addTo(menu, menuGroup, order++, R.string.get_user);
        } catch (Exception e) {
            MyLog.e(this, method, e);
        }

    }

    public boolean onContextItemSelected(MenuItem item) {
        MyAccount ma = getActingAccount();
        if (ma.isValid()) {
            ActorContextMenuItem contextMenuItem = ActorContextMenuItem.fromId(item.getItemId());
            MyLog.v(this, () -> "onContextItemSelected: " + contextMenuItem + "; actor="
                    + ma.getAccountName() + "; name=" + getViewItem().actor.getNamePreferablyWebFingerId());
            return contextMenuItem.execute(this, ma);
        } else {
            return false;
        }
    }

    @NonNull
    public ActorViewItem getViewItem() {
        if (mViewItem.isEmpty()) {
            return ActorViewItem.EMPTY;
        }
        if (ActivityViewItem.class.isAssignableFrom(mViewItem.getClass())) {
            return getViewItem(((ActivityViewItem) mViewItem));
        }
        return (ActorViewItem) mViewItem;
    }

    @NonNull
    protected ActorViewItem getViewItem(ActivityViewItem activityViewItem) {
        return activityViewItem.getObjActorItem();
    }

    public Origin getOrigin() {
        return getViewItem().actor.origin;
    }
}
