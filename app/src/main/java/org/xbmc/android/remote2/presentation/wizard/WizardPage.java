package org.xbmc.android.remote2.presentation.wizard;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ScrollView;

import org.xbmc.android.remote2.presentation.wizard.listener.PageCanFinishListener;

import java.util.ArrayList;

public abstract class WizardPage<T> extends ScrollView {

    protected T input = null;
    protected Wizard<T> wizard;
    private ArrayList<PageCanFinishListener> listeners = new ArrayList<PageCanFinishListener>();
    private boolean canFinish = false;

    public WizardPage(Context context, AttributeSet attrs, int defStyle, Wizard<T> wizard) {
        super(context, attrs, defStyle);
        this.wizard = wizard;
    }

    public WizardPage(Context context, AttributeSet attrs, Wizard<T> wizard) {
        super(context, attrs);
        this.wizard = wizard;
    }

    public WizardPage(Context context, Wizard<T> wizard) {
        super(context);
        this.wizard = wizard;
    }

    public void init() {
        inflate(getContext(), getLayoutId(), this);
        onInit();
    }

    public OnClickListener getNextClickListener() {
        return new OnClickListener() {
            public void onClick(View v) {
                showNextPage();
            }
        };
    }

    protected void showNextPage() {
        wizard.showNextPage();
    }

    public abstract WizardPage<T> getNextPage();

    public abstract int getLayoutId();

    protected abstract void onInit();

    protected void showBusyMessage(String msg) {
        wizard.showBusyMessage(msg);
    }

    protected void removeBusyMessage() {
        wizard.removeBusyMessage();
    }

    public T getInput() {
        return input;
    }

    public void setInput(T input) {
        this.input = input;
    }

    public boolean canFinish() {
        return canFinish;
    }

    protected void setCanFinish(boolean canFinish) {
        this.canFinish = canFinish;
        notifyCanFinishListeners();
    }

    private void notifyCanFinishListeners() {
        for (PageCanFinishListener listener : listeners) {
            listener.canFinish(canFinish);
        }
    }

    public void addCanFinishListener(PageCanFinishListener listener) {
        listeners.add(listener);
    }

    public void removeCanFinishListener(PageCanFinishListener listener) {
        listeners.remove(listener);
    }

    public void show() {
        Animation animation = AnimationUtils.makeInAnimation(getContext(), false);
        this.setAnimation(animation);
        setVisibility(View.VISIBLE);
    }

    public void hide() {
        Animation animation = AnimationUtils.makeOutAnimation(getContext(), false);
        this.setAnimation(animation);
        this.setVisibility(View.GONE);
    }

}
