package com.buquati.vipr;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;

import com.buquati.vipr.models.Item;

import java.util.List;

public interface Classifier {
    /**
     * An immutable result returned by a Classifier describing what was recognized.
     */
    public class Recognition {
        /**
         * A unique identifier for what has been recognized. Specific to the class, not the instance of
         * the object.
         */
        private final String id;

        /**
         * Display name for the recognition.
         */
        private final String title;

        private Item itemRecognt;

        /**
         * A sortable score for how good the recognition is relative to others. Higher should be better.
         */
        private final Float confidence;

        /** Optional location within the source image for the location of the recognized object. */
        private RectF location;

        public Recognition(
                final String id, final String title, final Float confidence, final RectF location,final Item item) {
            this.id = id;
            this.title = title;
            this.confidence = confidence;
            this.location = location;
            this.itemRecognt=item;
        }
        public Recognition(
                final String id, final String title, final Float confidence, final Item item) {
            this.id = id;
            this.title = title;
            this.confidence = confidence;
            this.itemRecognt = item;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public Float getConfidence() {
            return confidence;
        }

        public RectF getLocation() {
            return new RectF(location);
        }

        public void setLocation(RectF location) {
            this.location = location;
        }

        public Item getItemRecognt() {
            return itemRecognt;
        }

        public void setItemRecognt(Item itemRecognt) {
            this.itemRecognt = itemRecognt;
        }

        @Override
        public String toString() {
            String resultString = "";
            if (id != null) {
                resultString += "[" + id + "] ";
            }

            if (title != null) {
                resultString += title + " ";
            }

            if (confidence != null) {
                resultString += String.format("(%.1f%%) ", confidence * 100.0f);
            }

            if (location != null) {
                resultString += location + " ";
            }

            return resultString.trim();
        }
    }

    List<Recognition> recognizeImage(Bitmap bitmap, AssetManager assetManager) ;

    void enableStatLogging(final boolean debug);

    String getStatString();

    void close();
}
