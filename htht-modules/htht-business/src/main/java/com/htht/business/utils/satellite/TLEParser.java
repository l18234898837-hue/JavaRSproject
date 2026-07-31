package com.htht.business.utils.satellite;


import com.htcloud.orbit.OrbitHelper;
import org.orekit.propagation.analytical.tle.TLE;

public class TLEParser {

    public static void main(String[] args) {

        OrbitHelper.init("F:\\work\\orekit-data-master", "https://gitlab.orekit.org/orekit/orekit-data/-/archive/master/orekit-data-master.zip");

        String line1 = "1 39084U 13008A   25247.22279722  .00000727  00000-0  17136-3 0  9996";
        String line2 = "2 39084  98.2194 316.6990 0001211  93.4990 266.6347 14.57107349656251";

        try {
            TLE tle = new TLE(line1, line2);

            // 基本标识信息
            System.out.println("卫星编号: " + tle.getSatelliteNumber());
            System.out.println("分类: " + tle.getClassification());

            // 国际标识符
            System.out.println("发射年份: " + tle.getLaunchYear());
            System.out.println("发射编号: " + tle.getLaunchNumber());
            System.out.println("发射件次: " + tle.getLaunchPiece());
            System.out.println("国际标识符: " + tle.getLaunchYear() +
                "-" + tle.getLaunchNumber() +
                tle.getLaunchPiece());

            // 时间和历元信息
            System.out.println("历元: " + tle.getDate());

            // 轨道参数
            System.out.println("平均运动: " + tle.getMeanMotion() + " rad/s");
            System.out.println("平均运动一阶导数: " + tle.getMeanMotionFirstDerivative() + " rad/s²");
            System.out.println("平均运动二阶导数: " + tle.getMeanMotionSecondDerivative() + " rad/s³");
            System.out.println("轨道倾角: " + Math.toDegrees(tle.getI()) + "°");
            System.out.println("升交点赤经: " + Math.toDegrees(tle.getRaan()) + "°");
            System.out.println("偏心率: " + tle.getE());
            System.out.println("近地点幅角: " + Math.toDegrees(tle.getPerigeeArgument()) + "°");
            System.out.println("平近点角: " + Math.toDegrees(tle.getMeanAnomaly()) + "°");

            // 其他参数
            System.out.println("B*系数: " + tle.getBStar());
            System.out.println("星历类型: " + tle.getEphemerisType());
            System.out.println("元素集编号: " + tle.getElementNumber());
            System.out.println("历元时刻的圈数: " + tle.getRevolutionNumberAtEpoch());

            // 验证B*系数转换
            System.out.println("B*系数验证: " + tle.getBStar() + " (应为 1.7136E-4)");

        } catch (Exception e) {
            System.out.println("TLE解析失败: " + e.getMessage());
        }
    }


}
