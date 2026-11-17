package im.marin.onyxtv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.media.tv.TvContract;
import android.media.tv.TvView;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

/** Direct TvView player for hardware passthrough inputs (HDMI, Composite/AV) on Fire OS. */
public final class PassthroughActivity extends Activity {
    static final String EXTRA_INPUT_ID = "input_id";
    private TvView tvView;
    private String inputId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        tvView = new TvView(this);
        root.addView(tvView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        inputId = intent != null ? intent.getStringExtra(EXTRA_INPUT_ID) : null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (inputId == null || inputId.isEmpty()) {
            finish();
            return;
        }
        try {
            Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
            tvView.tune(inputId, uri);
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onPause() {
        if (tvView != null) {
            try {
                tvView.reset();
            } catch (RuntimeException ignored) {
            }
        }
        super.onPause();
    }
}
