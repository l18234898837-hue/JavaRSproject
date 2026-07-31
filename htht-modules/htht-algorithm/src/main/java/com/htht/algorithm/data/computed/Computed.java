package com.htht.algorithm.data.computed;

import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstConstants;

public abstract class Computed {

    public Dataset getDataset(String tilePath) {
        return gdal.Open(tilePath);
    }

    public double[] getBandData(Dataset dataset, int bandIdx) {
        Band band = dataset.GetRasterBand(bandIdx);
        int xSize = dataset.getRasterXSize();
        int ySize = dataset.getRasterYSize();
        double[] bandData = new double[xSize * ySize];
        band.ReadRaster(0, 0, xSize, ySize, gdalconstConstants.GDT_Float64, bandData);
        return bandData;
    }
}
