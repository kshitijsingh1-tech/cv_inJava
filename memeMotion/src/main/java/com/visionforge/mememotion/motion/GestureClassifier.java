package com.visionforge.mememotion.motion;

import com.visionforge.mememotion.detection.Keypoint;
import com.visionforge.mememotion.detection.PoseResult;

/**
 * Intelligent Rule-Based Gesture Classifier.
 * Analyzes MoveNet body landmarks, spatial vectors, and anatomical constraints
 * to unambiguously classify postures without collisions:
 *
 * 1. FACE COVERED (Facepalm / Despair):
 *    - One or both hands placed directly over the center of the face (eyes/nose/mouth).
 *
 * 2. HEAD HOLDING (I'm Cooked):
 *    - Both hands clutching the lateral temples/ears (framing the head with center face clear).
 *
 * 3. THE HONORED ONE (Gojo Satoru):
 *    - One hand held upright beside chin/jaw/cheek, elbow bent down at ribs, other hand down.
 *    - OR Domain Expansion clasp at chest.
 *
 * 4. HANDS RAISED (Both Hands Up) & ONE HAND RAISED (Hallelujah):
 *    - Arms elevated above shoulder level in the air.
 *
 * 5. T-POSE: Arms locked horizontally outward.
 * 6. DRAMATIC SHRUG: Hands wide at chest, elbows bent outward.
 * 7. LOOKING DOWN: Head dropped low toward shoulders.
 */
public class GestureClassifier {

    public static GestureType classify(PoseResult pose) {
        if (pose == null) return GestureType.NONE;

        // Key landmarks
        Keypoint nose = pose.get(0);
        Keypoint leftShoulder = pose.get(5);
        Keypoint rightShoulder = pose.get(6);
        Keypoint leftElbow = pose.get(7);
        Keypoint rightElbow = pose.get(8);
        Keypoint leftWrist = pose.get(9);
        Keypoint rightWrist = pose.get(10);

        boolean hasShoulders = pose.isConfident(5) && pose.isConfident(6);
        if (!hasShoulders) {
            return GestureType.NONE;
        }

        // Anatomical scale ruler
        float shoulderWidth = Math.abs(leftShoulder.getX() - rightShoulder.getX());
        if (shoulderWidth < 20) {
            return GestureType.NONE; // Too distant
        }

        float shoulderMidY = (leftShoulder.getY() + rightShoulder.getY()) / 2.0f;
        float shoulderMidX = (leftShoulder.getX() + rightShoulder.getX()) / 2.0f;

        // Head and Face vertical reference anchors
        boolean hasNose = pose.isConfident(0);
        float faceX = hasNose ? nose.getX() : shoulderMidX;
        float faceY = hasNose ? nose.getY() : (shoulderMidY - shoulderWidth * 0.35f);

        boolean leftWristConf = pose.isConfident(9);
        boolean rightWristConf = pose.isConfident(10);

        // ─────────────────────────────────────────────────────────────
        // 1. FACE COVERED (FACEPALM / DESPAIR / HANDS COVERING EYES/NOSE)
        // Hand(s) placed directly over the center of the face.
        // ─────────────────────────────────────────────────────────────
        // Two-handed face cover: both wrists centered directly on the face
        if (leftWristConf && rightWristConf) {
            boolean wristsNearFaceY = leftWrist.getY() < shoulderMidY
                    && rightWrist.getY() < shoulderMidY
                    && leftWrist.getY() > faceY - shoulderWidth * 0.25f
                    && rightWrist.getY() > faceY - shoulderWidth * 0.25f;

            if (wristsNearFaceY) {
                float wristSpan = Math.abs(leftWrist.getX() - rightWrist.getX());
                float wristsMidX = (leftWrist.getX() + rightWrist.getX()) / 2.0f;
                boolean centeredOnFace = Math.abs(wristsMidX - faceX) < shoulderWidth * 0.28f;

                // Hands are pressed together over the face (narrow span, centered)
                if (centeredOnFace && wristSpan < shoulderWidth * 0.38f) {
                    return GestureType.FACE_COVERED;
                }
            }
        }

        // Single-handed Facepalm: one wrist placed directly over the nose/eyes
        if (leftWristConf) {
            float distToFace = (float) Math.hypot(leftWrist.getX() - faceX, leftWrist.getY() - faceY);
            if (distToFace < shoulderWidth * 0.28f) {
                return GestureType.FACE_COVERED;
            }
        }
        if (rightWristConf) {
            float distToFace = (float) Math.hypot(rightWrist.getX() - faceX, rightWrist.getY() - faceY);
            if (distToFace < shoulderWidth * 0.28f) {
                return GestureType.FACE_COVERED;
            }
        }

        // ─────────────────────────────────────────────────────────────
        // 2. HEAD HOLDING ("I'M COOKED" / HANDS ON TEMPLES/EARS)
        // Both hands clutching the lateral sides of the skull.
        // Hands are on opposite sides of the head with wide span, face clear.
        // ─────────────────────────────────────────────────────────────
        if (leftWristConf && rightWristConf) {
            boolean leftAtHead = leftWrist.getY() < shoulderMidY - shoulderWidth * 0.05f
                    && leftWrist.getY() > faceY - shoulderWidth * 0.30f;
            boolean rightAtHead = rightWrist.getY() < shoulderMidY - shoulderWidth * 0.05f
                    && rightWrist.getY() > faceY - shoulderWidth * 0.30f;

            if (leftAtHead && rightAtHead) {
                float wristSpan = Math.abs(leftWrist.getX() - rightWrist.getX());
                float minWristX = Math.min(leftWrist.getX(), rightWrist.getX());
                float maxWristX = Math.max(leftWrist.getX(), rightWrist.getX());

                // Are hands on opposite sides of the head (flanking the skull)?
                boolean flankingHead = (minWristX < faceX - shoulderWidth * 0.10f)
                        && (maxWristX > faceX + shoulderWidth * 0.10f)
                        && (wristSpan >= shoulderWidth * 0.42f && wristSpan < shoulderWidth * 1.15f);

                if (flankingHead) {
                    return GestureType.HEAD_HOLDING;
                }
            }
        }

        // ─────────────────────────────────────────────────────────────
        // 3. THE HONORED ONE (GOJO SATORU - DOMAIN EXPANSION / CHIN SIGN)
        // One hand held upright beside chin/jaw/cheek, elbow pointing DOWN,
        // other arm relaxed or down on desk.
        // ─────────────────────────────────────────────────────────────
        // Check single-hand Gojo sign (Left hand)
        boolean leftElbowDown = !pose.isConfident(7) || (leftElbow.getY() > leftWrist.getY() + shoulderWidth * 0.08f);
        boolean leftGojoPose = leftWristConf && leftElbowDown
                && (leftWrist.getY() >= faceY - shoulderWidth * 0.10f)
                && (leftWrist.getY() <= shoulderMidY + shoulderWidth * 0.25f)
                && (Math.abs(leftWrist.getX() - faceX) < shoulderWidth * 0.45f)
                && (!rightWristConf || rightWrist.getY() > shoulderMidY - shoulderWidth * 0.10f);

        // Check single-hand Gojo sign (Right hand)
        boolean rightElbowDown = !pose.isConfident(8) || (rightElbow.getY() > rightWrist.getY() + shoulderWidth * 0.08f);
        boolean rightGojoPose = rightWristConf && rightElbowDown
                && (rightWrist.getY() >= faceY - shoulderWidth * 0.10f)
                && (rightWrist.getY() <= shoulderMidY + shoulderWidth * 0.25f)
                && (Math.abs(rightWrist.getX() - faceX) < shoulderWidth * 0.45f)
                && (!leftWristConf || leftWrist.getY() > shoulderMidY - shoulderWidth * 0.10f);

        // Check two-hand Domain Expansion clasp at chest
        boolean domainClasp = leftWristConf && rightWristConf
                && (Math.hypot(leftWrist.getX() - rightWrist.getX(), leftWrist.getY() - rightWrist.getY()) < shoulderWidth * 0.35f)
                && (leftWrist.getY() > faceY && leftWrist.getY() < shoulderMidY + shoulderWidth * 0.35f);

        if (leftGojoPose || rightGojoPose || domainClasp) {
            return GestureType.HONORED_ONE;
        }

        // ─────────────────────────────────────────────────────────────
        // 4. HANDS RAISED (BOTH HANDS UP) & ONE HAND RAISED (HALLELUJAH)
        // Wrist elevated above shoulder level in the air.
        // ─────────────────────────────────────────────────────────────
        float raisedThresholdY = shoulderMidY - shoulderWidth * 0.25f;
        boolean leftRaised = leftWristConf && (leftWrist.getY() < raisedThresholdY);
        boolean rightRaised = rightWristConf && (rightWrist.getY() < raisedThresholdY);

        if (leftRaised && rightRaised) {
            return GestureType.HANDS_RAISED; // Both hands raised high
        }

        if (leftRaised ^ rightRaised) {
            // Exactly one hand is raised high in the air
            return GestureType.ONE_HAND_RAISED; // Single hand up (Hallelujah)
        }

        // ─────────────────────────────────────────────────────────────
        // 5. T-POSE ("ASSERT DOMINANCE")
        // Wrists at shoulder level, arms locked horizontally outward
        // ─────────────────────────────────────────────────────────────
        if (leftWristConf && rightWristConf && pose.isConfident(7) && pose.isConfident(8)) {
            float leftWristYDiff = Math.abs(leftWrist.getY() - leftShoulder.getY());
            float rightWristYDiff = Math.abs(rightWrist.getY() - rightShoulder.getY());
            float wristDist = Math.abs(leftWrist.getX() - rightWrist.getX());

            boolean armsHorizontal = leftWristYDiff < shoulderWidth * 0.30f && rightWristYDiff < shoulderWidth * 0.30f;
            boolean armsSpreadWide = wristDist > shoulderWidth * 1.70f;

            if (armsHorizontal && armsSpreadWide) {
                return GestureType.T_POSE;
            }
        }

        // ─────────────────────────────────────────────────────────────
        // 6. DRAMATIC SHRUG ("I DON'T KNOW")
        // Wrists wider than shoulders, elbows bent outward, hands near chest level
        // ─────────────────────────────────────────────────────────────
        if (leftWristConf && rightWristConf) {
            float wristDist = Math.abs(leftWrist.getX() - rightWrist.getX());
            boolean handsNearChest = Math.abs(leftWrist.getY() - shoulderMidY) < shoulderWidth * 0.55f &&
                                     Math.abs(rightWrist.getY() - shoulderMidY) < shoulderWidth * 0.55f;
            boolean handsBelowNose = leftWrist.getY() > faceY && rightWrist.getY() > faceY;

            if (wristDist > shoulderWidth * 1.40f && handsNearChest && handsBelowNose) {
                return GestureType.DRAMATIC_SHRUG;
            }
        }

        // ─────────────────────────────────────────────────────────────
        // 7. LOOKING DOWN ("DEPRESSION MODE")
        // Head/nose dropped down low toward or below shoulder level
        // ─────────────────────────────────────────────────────────────
        if (hasNose) {
            if (nose.getY() > shoulderMidY - (shoulderWidth * 0.12f)) {
                return GestureType.LOOKING_DOWN;
            }
        }

        return GestureType.NONE;
    }
}
