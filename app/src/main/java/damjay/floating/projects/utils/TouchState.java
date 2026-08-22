package damjay.floating.projects.utils;

public class TouchState {
    private static TouchState instance;
    public static int moveTolerance = 5;
    private float finalX;
    private float finalY;
    private float initialX;
    private float initialY;
    private int maxDistanceMoved;
    private int originalX;
    private int originalY;

    private TouchState() {
    }

    public static TouchState newInstance() {
        return new TouchState();
    }

    public static TouchState getInstance() {
        if (instance == null) {
            instance = new TouchState();
        }
        return instance;
    }

    public void setInitialPosition(float initialX, float initialY) {
        this.initialX = initialX;
        this.initialY = initialY;
        setFinalPosition(initialX, initialY);
    }

    public void setFinalPosition(float finalX, float finalY) {
        this.finalX = finalX;
        this.finalY = finalY;
        this.maxDistanceMoved = (int) Math.max(this.maxDistanceMoved, Math.max(Math.abs(getMoveX()), Math.abs(getMoveY())));
    }

    public void setOriginalPosition(int x, int y) {
        this.originalX = x;
        this.originalY = y;
    }

    public int updatedPositionX() {
        int updatedX = this.originalX + ((int) getMoveX());
        return Math.max(updatedX, 0);
    }

    public int updatedPositionY() {
        int updatedY = this.originalY + ((int) getMoveY());
        return Math.max(updatedY, 0);
    }

    public float getMoveX() {
        return this.finalX - this.initialX;
    }

    public float getMoveY() {
        return this.finalY - this.initialY;
    }

    public boolean hasMoved() {
        if (this.maxDistanceMoved <= moveTolerance) {
            return Math.abs(getMoveX()) > ((float) moveTolerance) || Math.abs(getMoveY()) > ((float) moveTolerance);
        }
        this.maxDistanceMoved = 0;
        return true;
    }
}
