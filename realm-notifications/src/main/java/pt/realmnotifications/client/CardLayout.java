package pt.realmnotifications.client;

/** Pixel layout independent of Minecraft, shared by the HUD, previews and compact-screen checks. */
public final class CardLayout {
    public static final int HEIGHT=78, GAP=7, MAX_VISIBLE=3;
    private CardLayout() {}
    public static int width(int screenWidth) {return Math.max(120,Math.min(278,screenWidth-20));}
    public static int visible(int screenHeight) {return Math.clamp((screenHeight-42+GAP)/(HEIGHT+GAP),1,MAX_VISIBLE);}
    public static int x(int screenWidth,int cardWidth,String position) {
        if(position.endsWith("LEFT"))return 10;
        if(position.endsWith("CENTER"))return (screenWidth-cardWidth)/2;
        return screenWidth-cardWidth-10;
    }
    public static int y(int screenHeight,int slot,String position) {
        return position.startsWith("BOTTOM")?screenHeight-HEIGHT-42-slot*(HEIGHT+GAP):12+slot*(HEIGHT+GAP);
    }
    public static double ease(double value) {double t=Math.clamp(value,0,1);return 1-Math.pow(1-t,3);}
}
