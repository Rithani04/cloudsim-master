package org.cloudbus.cloudsim.examples.baseline.thresholdautoscaler;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Generate graphs from the latest CSV files using only standard Java (no Python/JFreeChart). */
public final class CloudSimEvaluation {
    private CloudSimEvaluation() {}
    private static final int W=1100,H=620,L=95,R=45,T=65,B=95;
    private static final Color BLUE=new Color(38,100,180), GREEN=new Color(24,139,101), RED=new Color(190,60,70);

    /** One-call entry point: print evaluation and refresh graphs. */
    public static void generate(Path results) throws IOException {
        Map<String, String> summary = firstRow(results.resolve("summary.csv"));
        if (summary.isEmpty()) {
            throw new IOException("Missing or empty summary.csv: " + results.resolve("summary.csv"));
        }
        printReport(results, summary);
        generateGraphs(results);
    }

    private static void generateGraphs(Path results) throws IOException {
        Path graphs=results.resolve("graphs");
        Files.createDirectories(graphs);
        // Remove old PNGs so skipped graphs never remain from a previous run.
        try (DirectoryStream<Path> ds=Files.newDirectoryStream(graphs,"*.png")) {
            for(Path p:ds) Files.delete(p);
        }
        List<Map<String,String>> series=read(results.resolve("timeseries.csv"));
        List<Map<String,String>> events=read(results.resolve("scaling_events.csv"));
        List<Map<String,String>> cloudlets=read(results.resolve("cloudlets.csv"));
        plotSeries(series,"cpu_percent","CPU demand over simulation time","CPU demand (%)",BLUE,graphs.resolve("cpu_utilization.png"));
        plotSeries(series,"active_vms","Provisioned VMs over simulation time","VM count",GREEN,graphs.resolve("active_vms.png"));
        plotSeries(series,"memory_percent","Memory utilization over simulation time","Memory (%)",BLUE,graphs.resolve("memory_utilization.png"));
        plotEvents(events,graphs.resolve("scaling_events.png"));
        plotLatency(cloudlets,graphs.resolve("response_time_distribution.png"));
        System.out.println("Graphs written to: "+graphs.toAbsolutePath());
    }
    private static List<Map<String,String>> read(Path p) throws IOException {
        List<Map<String,String>> out=new ArrayList<>();
        if(!Files.exists(p)) {System.out.println("Missing CSV: "+p);return out;}
        try(BufferedReader br=Files.newBufferedReader(p,StandardCharsets.UTF_8)) {
            String first=br.readLine(); if(first==null)return out;
            List<String> headers=splitCsv(first);
            String line;
            while((line=br.readLine())!=null){
                if(line.trim().isEmpty())continue;
                List<String> values=splitCsv(line); Map<String,String> row=new HashMap<>();
                for(int i=0;i<headers.size();i++)row.put(headers.get(i),i<values.size()?values.get(i):"");
                out.add(row);
            }
        }
        return out;
    }
    private static List<String> splitCsv(String line){
        List<String> fields=new ArrayList<>(); StringBuilder b=new StringBuilder();boolean quoted=false;
        for(int i=0;i<line.length();i++){
            char c=line.charAt(i);
            if(c=='"') {if(quoted && i+1<line.length() && line.charAt(i+1)=='"'){b.append('"');i++;}else quoted=!quoted;}
            else if(c==','&&!quoted){fields.add(b.toString());b.setLength(0);}else b.append(c);
        }
        fields.add(b.toString());return fields;
    }
    private static double num(String s){try{return Double.parseDouble(s);}catch(Exception e){return Double.NaN;}}
    private static double val(Map<String,String> r,String key){return num(r.get(key));}
    private static void base(Graphics2D g,String title,String xLabel,String yLabel,double minX,double maxX,double minY,double maxY){
        g.setColor(Color.WHITE);g.fillRect(0,0,W,H);g.setColor(new Color(35,42,52));
        g.setFont(new Font("SansSerif",Font.BOLD,22));g.drawString(title,L,37);
        g.setFont(new Font("SansSerif",Font.PLAIN,14));g.drawString(xLabel,W/2-50,H-25);
        g.rotate(-Math.PI/2);g.drawString(yLabel,-H/2-55,28);g.rotate(Math.PI/2);
        g.setFont(new Font("SansSerif",Font.PLAIN,12));
        for(int i=0;i<=5;i++){
            double f=i/5.0;int x=L+(int)Math.round(f*(W-L-R));int y=H-B-(int)Math.round(f*(H-T-B));
            g.setColor(new Color(226,231,236));g.drawLine(x,T,x,H-B);g.drawLine(L,y,W-R,y);
            g.setColor(new Color(75,85,95));g.drawString(String.format(Locale.US,"%.1f",minX+f*(maxX-minX)),x-17,H-B+22);
            g.drawString(String.format(Locale.US,"%.1f",minY+f*(maxY-minY)),15,y+4);
        }
        g.setColor(Color.DARK_GRAY);g.drawRect(L,T,W-L-R,H-T-B);
    }
    private static int px(double x,double lo,double hi){return L+(int)Math.round((x-lo)/(hi-lo)*(W-L-R));}
    private static int py(double y,double lo,double hi){return H-B-(int)Math.round((y-lo)/(hi-lo)*(H-T-B));}
    private static void plotSeries(List<Map<String,String>> rows,String field,String title,String label,Color color,Path out)throws IOException{
        List<double[]> pts=new ArrayList<>();double x0=Double.POSITIVE_INFINITY,x1=Double.NEGATIVE_INFINITY,ymax=0;
        for(Map<String,String> r:rows){double x=val(r,"time"),y=val(r,field);if(!Double.isFinite(x)||!Double.isFinite(y))continue;
            pts.add(new double[]{x,y});x0=Math.min(x0,x);x1=Math.max(x1,x);ymax=Math.max(ymax,y);}
        if(pts.isEmpty()){System.out.println("Skipping "+out.getFileName()+" (no valid samples)");return;}
        pts.sort(Comparator.comparingDouble(a->a[0]));if(x1<=x0)x1=x0+1;double y1=Math.max(1,ymax*1.1);
        BufferedImage img=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);Graphics2D g=img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        base(g,title,"Simulation time",label,x0,x1,0,y1);g.setColor(color);g.setStroke(new BasicStroke(2.6f));
        for(int i=1;i<pts.size();i++){double[] a=pts.get(i-1),b=pts.get(i);g.draw(new Line2D.Double(px(a[0],x0,x1),py(a[1],0,y1),px(b[0],x0,x1),py(b[1],0,y1)));}
        g.dispose();ImageIO.write(img,"png",out.toFile());
    }
    private static void plotEvents(List<Map<String,String>> rows,Path out)throws IOException{
        List<double[]> pts=new ArrayList<>();double maxX=1;
        for(Map<String,String> r:rows){double t=val(r,"time"),d=val(r,"delta");if(Double.isFinite(t)&&Double.isFinite(d)&&d!=0){pts.add(new double[]{t,d});maxX=Math.max(maxX,t);}}
        if(pts.isEmpty()){System.out.println("Skipping scaling_events.png (no scaling events)");return;}
        BufferedImage img=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);Graphics2D g=img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        base(g,"Scaling events (+ scale out, - scale in)","Simulation time","Scaling action",0,maxX, -1.5,1.5);
        for(double[] p:pts){int x=px(p[0],0,maxX);g.setColor(p[1]>0?GREEN:RED);g.setStroke(new BasicStroke(3f));g.drawLine(x,py(0,-1.5,1.5),x,py(Math.signum(p[1]),-1.5,1.5));g.fillOval(x-5,py(Math.signum(p[1]),-1.5,1.5)-5,10,10);}
        g.dispose();ImageIO.write(img,"png",out.toFile());
    }
    private static void plotLatency(List<Map<String,String>> rows,Path out)throws IOException{
        List<Double> latencies=new ArrayList<>();double max=0;
        for(Map<String,String> r:rows){double x=val(r,"response_time");if(Double.isFinite(x)&&x>=0){latencies.add(x);max=Math.max(max,x);}}
        if(latencies.isEmpty()){System.out.println("Skipping response_time_distribution.png (no valid latencies)");return;}
        int bins=20;int[] counts=new int[bins];double upper=Math.max(1,max*1.001);
        for(double x:latencies)counts[Math.min(bins-1,(int)(x/upper*bins))]++;
        int high=1;for(int c:counts)high=Math.max(high,c);
        BufferedImage img=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);Graphics2D g=img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        base(g,"Request response-time distribution","Response time","Cloudlet count",0,upper,0,high*1.1);
        g.setColor(BLUE);for(int i=0;i<bins;i++){int x=px(i*upper/bins,0,upper),xNext=px((i+1)*upper/bins,0,upper),y=py(counts[i],0,high*1.1);
            g.fillRect(x+1,y,Math.max(1,xNext-x-2),H-B-y);}
        g.dispose();ImageIO.write(img,"png",out.toFile());
    }
    private static void printReport(Path resultsDirectory, Map<String, String> s) throws IOException {
        List<Map<String, String>> events = read(resultsDirectory.resolve("scaling_events.csv"));
        int scaleOut = 0, scaleIn = 0;
        for (Map<String, String> event : events) {
            double delta = num(event.get("delta"));
            if (Double.isFinite(delta)) {
                if (delta > 0) scaleOut++;
                if (delta < 0) scaleIn++;
            }
        }
        String workload = s.getOrDefault("workload", "UNKNOWN");
        System.out.println("\n========================================================");
        System.out.println("            CLOUD AUTOSCALING EVALUATION");
        System.out.println("========================================================");
        line("Controller", s.get("controller"));
        line("Workload", workload);
        line("SLA threshold", value(s, "sla_limit", " time units"));

        section("A. PERFORMANCE");
        line("Generated / scheduled", s.get("submitted"));
        line("Completed successfully", s.get("completed_success"));
        line("Completion rate", value(s, "completion_rate_percent", "%"));
        line("Average response time", value(s, "avg_response_time", " time units"));
        line("P95 response time", value(s, "p95_response_time", " time units"));
        line("Completed-request SLA violations", value(s, "sla_violation_rate_completed_percent", "%"));
        line("SLA miss or unfinished", value(s, "sla_miss_or_unfinished_percent", "%"));
        line("Missing arrival timestamps", s.get("missing_arrival_count"));

        section("B. RESOURCE EFFICIENCY");
        line("Average CPU demand", value(s, "avg_cpu_percent", "%"));
        line("Average memory utilization", value(s, "avg_memory_percent", "%"));
        line("Average provisioned VMs", value(s, "avg_active_vms", ""));
        line("VM-time", value(s, "vm_time", " VM-time units"));
        line("Estimated cost (proxy)", value(s, "estimated_cost", " cost units"));

        section("C. SCALING STABILITY");
        line("Observed scaling events", s.get("scaling_events"));
        line("Direction reversals", s.get("scaling_oscillations"));
        line("Scale-out events", Integer.toString(scaleOut));
        line("Scale-in events", Integer.toString(scaleIn));

        section("D. ROBUSTNESS");
        line("Workload regime", workload);
        line("Distribution shift", "NOT EVALUATED");
        line("SLA degradation", "N/A");
        line("Latency degradation", "N/A");
        line("Cost degradation", "N/A");
        System.out.println("  Requires comparable familiar and shifted workload runs.");

        section("OUTPUT");
        line("CSV directory", resultsDirectory.toAbsolutePath().toString());
        System.out.println("  Note: CPU is demand, not necessarily allocated utilization.");
        System.out.println("  Note: submitted includes generated/scheduled requests, possibly after simulation end.");
        System.out.println("  Note: scaling counts reflect recorded VM-count changes, not necessarily exact ACK times.");
        System.out.println("========================================================\n");
    }

    private static void section(String title) {
        System.out.println("\n---------------- " + title + " ----------------");
    }
    private static void line(String label, String value) {
        System.out.printf(Locale.US, "%-35s : %s%n", label, value == null ? "N/A" : value);
    }
    private static String value(Map<String, String> row, String key, String suffix) {
        double n = num(row.get(key));
        return Double.isFinite(n) ? String.format(Locale.US, "%.2f%s", n, suffix) : "N/A";
    }
    private static Map<String, String> firstRow(Path file) throws IOException {
        List<Map<String, String>> rows = read(file);
        return rows.isEmpty() ? Collections.emptyMap() : rows.get(0);
    }
}