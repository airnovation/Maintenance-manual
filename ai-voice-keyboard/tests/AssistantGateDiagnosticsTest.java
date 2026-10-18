package com.airnovation.aivoicekeyboard;
import java.util.*;
public final class AssistantGateDiagnosticsTest {
    static int checks;
    static final EnumSet<AssistantNodeFilter.Reason> covered=EnumSet.noneOf(AssistantNodeFilter.Reason.class);
    static void check(boolean condition) { checks++; if (!condition) throw new AssertionError("gate check "+checks); }
    static AssistantNodeFilter.Layer layer(int id,int order,boolean supported,AssistantNodeFilter.Kind kind,boolean error) {
        return new AssistantNodeFilter.Layer(id,order,supported,kind,error);
    }
    static AssistantNodeFilter.Layer ordinary(int id,int order) {
        return layer(id,order,true,AssistantNodeFilter.Kind.VIEW,false);
    }
    static void expect(AssistantNodeFilter.Reason reason,List<AssistantNodeFilter.Layer> a,List<AssistantNodeFilter.Layer> b) {
        var d=AssistantNodeFilter.classifyPaths(a,b); check(d.reason==reason); covered.add(reason);
        check(d.allowed()==(reason==AssistantNodeFilter.Reason.ALLOW));
    }
    static boolean legacy(List<AssistantNodeFilter.Layer> a,List<AssistantNodeFilter.Layer> b) {
        int i=0; while(i<a.size() && i<b.size() && a.get(i).nodeId==b.get(i).nodeId)i++;
        if(i==0 || i==a.size() || i==b.size())return false;
        for(int j=i;j<a.size();j++)if(!a.get(j).supported)return false;
        for(int j=i;j<b.size();j++)if(!b.get(j).supported)return false;
        return a.get(i).drawingOrder>0 && b.get(i).drawingOrder>0 && a.get(i).drawingOrder<b.get(i).drawingOrder;
    }
    public static void main(String[] args) {
        var root=ordinary(777,0); var lo=ordinary(888,1); var hi=ordinary(999,2);
        List<AssistantNodeFilter.Layer> a=List.of(root,lo), b=List.of(root,hi);
        expect(AssistantNodeFilter.Reason.ALLOW,a,b);
        expect(AssistantNodeFilter.Reason.PATH_MISSING,List.of(),b);
        expect(AssistantNodeFilter.Reason.ROOT_DIFFERENT,List.of(ordinary(111,1)),b);
        expect(AssistantNodeFilter.Reason.PATH_IDENTICAL,a,a);
        expect(AssistantNodeFilter.Reason.MASK_ANCESTOR,List.of(root),b);
        expect(AssistantNodeFilter.Reason.MESSAGE_ANCESTOR,a,List.of(root));
        expect(AssistantNodeFilter.Reason.MASK_CLASS,List.of(root,layer(888,1,false,AssistantNodeFilter.Kind.OTHER,false)),b);
        expect(AssistantNodeFilter.Reason.MESSAGE_CLASS,a,List.of(root,layer(999,2,false,AssistantNodeFilter.Kind.MISSING,false)));
        expect(AssistantNodeFilter.Reason.MASK_CLASS,List.of(root,layer(888,1,false,AssistantNodeFilter.Kind.NONE,false)),b);
        expect(AssistantNodeFilter.Reason.MESSAGE_CLASS,a,List.of(root,layer(999,2,false,AssistantNodeFilter.Kind.NONE,false)));
        expect(AssistantNodeFilter.Reason.MASK_ORDER_ERROR,List.of(root,layer(888,0,true,AssistantNodeFilter.Kind.VIEW,true)),b);
        expect(AssistantNodeFilter.Reason.MESSAGE_ORDER_ERROR,a,List.of(root,layer(999,0,true,AssistantNodeFilter.Kind.VIEW,true)));
        expect(AssistantNodeFilter.Reason.MASK_ORDER_ZERO,List.of(root,ordinary(888,0)),b);
        expect(AssistantNodeFilter.Reason.MESSAGE_ORDER_ZERO,a,List.of(root,ordinary(999,0)));
        expect(AssistantNodeFilter.Reason.MASK_ORDER_NEGATIVE,List.of(root,ordinary(888,-1)),b);
        expect(AssistantNodeFilter.Reason.MESSAGE_ORDER_NEGATIVE,a,List.of(root,ordinary(999,-1)));
        expect(AssistantNodeFilter.Reason.ORDER_EQUAL,a,List.of(root,ordinary(999,1)));
        expect(AssistantNodeFilter.Reason.ORDER_AHEAD,b,a);
        var box=new AssistantNodeFilter.Box(0,0,10,10);
        var empty=new AssistantNodeFilter.Box(0,0,0,10);
        var outside=new AssistantNodeFilter.Box(0,0,5,5);
        var bad=AssistantNodeFilter.assess(empty,box,List.of(),a,List.of());
        check(!bad.allowed && bad.decisions.get(0).reason==AssistantNodeFilter.Reason.GEOMETRY_INVALID);
        covered.add(bad.decisions.get(0).reason);
        bad=AssistantNodeFilter.assess(box,outside,List.of(),a,List.of());
        check(!bad.allowed && bad.decisions.get(0).reason==AssistantNodeFilter.Reason.OUTSIDE_CLIP);
        covered.add(bad.decisions.get(0).reason);
        bad=AssistantNodeFilter.assess(box,box,List.of(),b,List.of(new AssistantNodeFilter.Region(888,box,777,1,1,a)));
        check(!bad.allowed && bad.decisions.get(0).reason==AssistantNodeFilter.Reason.MASK_KIND);
        covered.add(bad.decisions.get(0).reason);
        check(covered.equals(EnumSet.allOf(AssistantNodeFilter.Reason.class)));
        // Exhaustively compare the v15 predicate on all meaningful order/support combinations.
        for(int x=-2;x<=4;x++) for(int y=-2;y<=4;y++) for(int flags=0;flags<16;flags++) {
            var left=List.of(root,layer(888,x,(flags&1)==0,AssistantNodeFilter.Kind.OTHER,false),
                    layer(889,0,(flags&2)==0,AssistantNodeFilter.Kind.OTHER,false));
            var right=List.of(root,layer(999,y,(flags&4)==0,AssistantNodeFilter.Kind.OTHER,false),
                    layer(990,0,(flags&8)==0,AssistantNodeFilter.Kind.OTHER,false));
            check(AssistantNodeFilter.classifyPaths(left,right).allowed()==legacy(left,right));
        }
        // Keep all gate counts, cap detailed combinations, never emit local node IDs or coordinates.
        var diagnostic=new AssistantCaptureDiagnostics("15-host-fixture");
        for(int i=0;i<12;i++) {
            var assessment=new AssistantNodeFilter.Assessment();
            assessment.add(new AssistantNodeFilter.Decision(AssistantNodeFilter.Reason.MASK_CLASS,
                    AssistantNodeFilter.Kind.OTHER,AssistantNodeFilter.Kind.NONE,AssistantNodeFilter.Order.POSITIVE,
                    AssistantNodeFilter.Order.POSITIVE,AssistantNodeFilter.Shape.values()[i%5],
                    AssistantNodeFilter.Shape.values()[i/5],AssistantNodeFilter.Kind.VIEW,AssistantNodeFilter.Kind.LAYOUT));
            diagnostic.assessment(assessment);
        }
        String output=diagnostic.metadataResult(1);
        check(output.contains("版本：15-host-fixture"));
        check(output.contains("MASK_CLASS=12")); check(output.contains("DETAIL_LIMIT=8"));
        check(!output.contains("777") && !output.contains("888") && !output.contains("999"));
        check(!output.contains("android.") && !output.contains("com.whatsapp"));
        var multi=AssistantNodeFilter.assess(box,box,List.of(),b,List.of(
                new AssistantNodeFilter.Region(888,box,777,1,3,a),
                new AssistantNodeFilter.Region(123,box,777,1,1,a)));
        check(!multi.allowed && multi.decisions.size()==2);
        check(multi.decisions.get(0).allowed() && !multi.decisions.get(1).allowed());
        System.out.println("PASS "+checks+" gate diagnostics checks; all "+covered.size()+" reasons covered; v15 predicate equivalence");
    }
}
