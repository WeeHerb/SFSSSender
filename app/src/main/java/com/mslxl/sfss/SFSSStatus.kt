package com.mslxl.sfss

sealed class SFSSStatus{
    class Ready : SFSSStatus() {
        override fun onExit() {

        }

        override fun onEnter() {

        }

    }
    class Disconnect : SFSSStatus() {
        override fun onExit() {

        }

        override fun onEnter() {

        }

    }

    abstract fun onExit();
    abstract fun onEnter();
}


