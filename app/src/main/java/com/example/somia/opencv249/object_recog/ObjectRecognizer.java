package com.example.somia.opencv249.object_recog;

import android.util.Log;

import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.DMatch;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ObjectRecognizer {


    private FeatureDetector featureDetector;
    private DescriptorExtractor descriptorExtractor;
    private DescriptorMatcher descriptorMatcher;

    private ArrayList<Mat> trainImages;
    private ArrayList<MatOfKeyPoint> trainKeypoints;
    private ArrayList<Mat> trainDescriptors;
    private ArrayList<String> objectNames;

    private MatchingStrategy matchingStrategy = MatchingStrategy.RATIO_TEST;

    private int numMatches;
    private int matchIndex;
    private int[] numMatchesInImage;

    public ObjectRecognizer(File trainDir) {

        ArrayList<File> jpgFiles = Utilities.getJPGFiles(trainDir);
        trainImages = Utilities.getImageMats(jpgFiles); //gives converted grey scale
        objectNames = Utilities.getFileNames(jpgFiles);

        featureDetector = FeatureDetector.create(FeatureDetector.ORB);
        descriptorExtractor = DescriptorExtractor.create(DescriptorExtractor.ORB);
        descriptorMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE);

        trainKeypoints = new ArrayList<MatOfKeyPoint>(); //for keypoints
        trainDescriptors = new ArrayList<Mat>(); //descript key points

        for (int i = 0; i < trainImages.size(); i++) {

            trainKeypoints.add(new MatOfKeyPoint());
            featureDetector.detect(trainImages.get(i), trainKeypoints.get(i)); // amount of key points and stablity
            trainDescriptors.add(new Mat());
            descriptorExtractor.compute(trainImages.get(i), trainKeypoints.get(i),
                    trainDescriptors.get(i)); // decriptor of each key point

        }
        descriptorMatcher.add(trainDescriptors);
        descriptorMatcher.train();
    }

    public void removeObject(int clickedImgIdx) {
        trainImages.remove(clickedImgIdx);
        objectNames.remove(clickedImgIdx);
        trainKeypoints.remove(clickedImgIdx);
        trainDescriptors.remove(clickedImgIdx);

        descriptorMatcher.clear();
        descriptorMatcher.add(trainDescriptors);
        descriptorMatcher.train();
    }
//We got a grey scale image (current scene)
    public String recognize(Mat mGray) {
        MatOfKeyPoint keypoints = new MatOfKeyPoint();
        Mat descriptors = new Mat();
        List<MatOfDMatch> matches = new LinkedList<MatOfDMatch>();
//Detected Keypoints of Current object
        featureDetector.detect(mGray, keypoints);
        //Computed Descriptor
        descriptorExtractor.compute(mGray, keypoints, descriptors);
        //Send for matching
        return match(keypoints, descriptors, matches, matchingStrategy);//Current Keypoints,Its descriptors, Matchs (how many will be matched), strategy
    }

    // Parameters for matching
    public static final double RATIO_TEST_RATIO = 0.92;
    public static final int RATIO_TEST_MIN_NUM_MATCHES = 32;

    public String match(MatOfKeyPoint keypoints, Mat descriptors, List<MatOfDMatch> matches, MatchingStrategy matchingStrategy) {

        return match_ratioTest(descriptors, matches, RATIO_TEST_RATIO, RATIO_TEST_MIN_NUM_MATCHES);

    }

    private String match_ratioTest(Mat descriptors, List<MatOfDMatch> matches, double ratio, int minNumMatches) {

        getMatches_ratioTest(descriptors, matches, ratio);
        return getDetectedObjIndex(matches, minNumMatches);
    }

    // adds to the matches list matches that satisfy the ratio test with ratio

    private void getMatches_ratioTest(Mat descriptors, List<MatOfDMatch> matches, double ratio) {

        LinkedList<MatOfDMatch> knnMatches = new LinkedList<MatOfDMatch>();
        DMatch bestMatch, secondBestMatch;

        descriptorMatcher.knnMatch(descriptors, knnMatches, 2); //normtype , k=2 for  ratio test.

        for (MatOfDMatch matOfDMatch : knnMatches) {
            bestMatch = matOfDMatch.toArray()[0];
            secondBestMatch = matOfDMatch.toArray()[1];

            if (bestMatch.distance / secondBestMatch.distance <= ratio) {

                MatOfDMatch goodMatch = new MatOfDMatch();
                goodMatch.fromArray(new DMatch[] { bestMatch });
                matches.add(goodMatch);

            }
        }
    }

    // uses the list of matches to count the number of matches to each database object.
    // The object with the maximum such number nmax is considered to have been recognized if nmax > minNumMatches.
    // if for a query descriptor there exists multiple matches to train
    // descriptors of the same train image, all such matches are counted as only
    // one match.

    private String getDetectedObjIndex(List<MatOfDMatch> matches, int minNumMatches) {

        numMatchesInImage = new int[trainImages.size()];
        matchIndex = -1;
        numMatches = 0;

        for (MatOfDMatch matOfDMatch : matches) {

            DMatch[] dMatch = matOfDMatch.toArray();
            boolean[] imagesMatched = new boolean[trainImages.size()];

            for (int i = 0; i < dMatch.length; i++)
            {
                if (!imagesMatched[dMatch[i].imgIdx])
                {
                    numMatchesInImage[dMatch[i].imgIdx]++;
                    imagesMatched[dMatch[i].imgIdx] = true;
                }
            }
        }

        for (int i = 0; i < numMatchesInImage.length; i++)
        {
            if (numMatchesInImage[i] > numMatches)
            {
                matchIndex = i;
                numMatches = numMatchesInImage[i];
            }
        }

        if (numMatches < minNumMatches)
        {
            return "-";
        }
        else
        {
            return objectNames.get(matchIndex);
        }
    }
}
