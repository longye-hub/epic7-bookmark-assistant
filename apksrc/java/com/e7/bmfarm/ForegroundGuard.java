package com.e7.bmfarm;
public final class ForegroundGuard {
    public static boolean isGameFocused(String dump,int expectedUid) {
        if(dump==null||expectedUid<0)return false;
        java.util.regex.Matcher focus=java.util.regex.Pattern.compile("(?m)^\\s*mCurrentFocus=Window\\{([0-9a-f]+)\\s+u\\d+\\s+com\\.zlongame\\.cn\\.epicseven/kr\\.supercreative\\.epic7\\.AppActivity\\}").matcher(dump);
        if(!focus.find())return false;
        String id=java.util.regex.Pattern.quote(focus.group(1));
        java.util.regex.Matcher window=java.util.regex.Pattern.compile("(?ms)^\\s*Window #\\d+ Window\\{"+id+"\\s+u\\d+\\s+[^\\n]*\\}:\\s*\\n(.*?)(?=^\\s*Window #\\d+ |\\z)").matcher(dump);
        if(!window.find())return false;
        java.util.regex.Matcher owner=java.util.regex.Pattern.compile("(?m)^\\s*mOwnerUid=(\\d+)\\b").matcher(window.group(1));
        if(!owner.find())return false;
        try{return Integer.parseInt(owner.group(1))==expectedUid;}catch(NumberFormatException e){return false;}
    }
}
