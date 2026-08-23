package com.htht.multisource.application.port.out;

import com.htht.multisource.application.port.out.model.PreflightSession;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.ProductCandidate;

import java.util.List;

public interface PreflightSessionStore {

    PreflightSession open(AccessSubject owner, List<InputAsset> inputs, List<ProductCandidate> candidates);

    SelectionLease acquire(AccessSubject owner, String token, List<String> candidateIds);

    interface SelectionLease {
        List<ProductCandidate> candidates();

        void commit();

        void release();
    }
}
