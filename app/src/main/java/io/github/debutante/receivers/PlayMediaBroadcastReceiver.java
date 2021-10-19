
package io.github.debutante.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;

import io.github.debutante.helper.EntityHelper;
import io.github.debutante.helper.L;
import io.github.debutante.helper.MediaBrowserHelper;
import io.github.debutante.helper.Obj;
import io.github.debutante.model.PlayMediaViewModel;
import io.github.debutante.persistence.EntityRepository;

public class PlayMediaBroadcastReceiver extends BroadcastReceiver {
    public static final String ACTION = PlayMediaBroadcastReceiver.class.getSimpleName() + "-ACTION";
    public static final String MEDIA_ID_KEY = PlayMediaBroadcastReceiver.class.getSimpleName() + "-MEDIA_ID_KEY";
    public static final String PARENT_MEDIA_ID_KEY = PlayMediaBroadcastReceiver.class.getSimpleName() + "-PARENT_MEDIA_ID_KEY";
    private final EntityRepository repository;
    private final PlayMediaViewModel playMediaViewModel;

    public PlayMediaBroadcastReceiver(PlayMediaViewModel playMediaViewModel, EntityRepository repository) {
        this.playMediaViewModel = playMediaViewModel;
        this.repository = repository;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String parentId = EntityHelper.mediaId(intent.getStringExtra(PARENT_MEDIA_ID_KEY), Collections.singletonMap(MediaBrowserHelper.RECURSIVE_CHILDREN_LOADING, "true"));
        if (StringUtils.isNotEmpty(parentId)) {
            MediaBrowserHelper.load(context, repository, parentId, p -> MediaBrowserHelper.loadChildren(context, repository, parentId, children -> {
                if (CollectionUtils.isNotEmpty(children)) {
                    playMediaViewModel.put(p, children, intent.getStringExtra(MEDIA_ID_KEY));
                }
            }));
        }

    }

    private static void logBroadcast(Intent intent) {
        L.i("Broadcasting " + ACTION + " " + L.toString(intent.getExtras()));
    }

    public static void broadcast(Context context, String parentMediaId, String mediaId) {
        Intent intent = new Intent(PlayMediaBroadcastReceiver.ACTION);
        intent.putExtra(PARENT_MEDIA_ID_KEY, parentMediaId);
        if (mediaId != null) {
            intent.putExtra(MEDIA_ID_KEY, mediaId);
        }
        context.sendBroadcast(Obj.tap(intent, PlayMediaBroadcastReceiver::logBroadcast));
    }
}
