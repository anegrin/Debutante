package io.github.debutante;

import androidx.fragment.app.Fragment;

public abstract class BaseFragment extends Fragment {
    protected Debutante d() {
        return (Debutante) requireActivity().getApplication();
    }
}
