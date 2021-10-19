package io.github.debutante.model.api;

import io.github.debutante.R;

public final class Error {
    public String message;
    public int code;

    public int stringResId() {
        switch (code) {
            case 10:
                return R.string._subsonic_error_code_10;
            case 20:
                return R.string._subsonic_error_code_20;
            case 30:
                return R.string._subsonic_error_code_30;
            case 40:
                return R.string._subsonic_error_code_40;
            case 41:
                return R.string._subsonic_error_code_41;
            case 50:
                return R.string._subsonic_error_code_50;
            case 60:
                return R.string._subsonic_error_code_60;
            case 70:
                return R.string._subsonic_error_code_70;
            default:
                return R.string._subsonic_error_code_0;
        }
    }
}
