package com.htcloud.orbit.propagation.events;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygonal;
import org.orekit.bodies.BodyShape;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.AbstractDetector;
import org.orekit.propagation.events.AdaptableInterval;
import org.orekit.propagation.events.handlers.EventHandler;

/**
 *  进入/离开多边形区域探测器 参考：{@link org.orekit.propagation.events.GeographicZoneDetector}
 */
public class PolygonalDetector extends AbstractDetector<PolygonalDetector> {
    /** Body on which the geographic zone is defined. */
    private BodyShape body;

    /**
     * 区域几何对象（面）
     */
    private Geometry zone;

    /**
     * 边框
     */
    private Envelope envelope;

    /** Margin to apply to the zone. */
    private final double margin;

    /** Protected constructor with full parameters.
     * <p>
     * This constructor is not public as users are expected to use the builder
     * API with the various {@code withXxx()} methods to set up the instance
     * in a readable manner without using a huge amount of parameters.
     * </p>
     * @param maxCheck maximum checking interval
     * @param threshold convergence threshold (s)
     * @param maxIter maximum number of iterations in the event time search
     * @param handler event handler to call at event occurrences
     * @param body body on which the geographic zone is defined
     * @param zone geographic zone to consider
     * @param margin angular margin to apply to the zone
     */
    public PolygonalDetector(final AdaptableInterval maxCheck, final double threshold,
                                final int maxIter, final EventHandler handler,
                                final BodyShape body,
                                final Polygonal zone,
                                final double margin) {
        super(maxCheck, threshold, maxIter, handler);
        this.body   = body;
        this.zone   = (Geometry) zone;
        this.envelope = this.zone.getEnvelopeInternal();
        this.margin = margin;
    }

    @Override
    public double g(SpacecraftState s) {
        // convert state to geodetic coordinates
        final GeodeticPoint gp = body.transform(s.getPosition(),
                s.getFrame(), s.getDate());
        // TODO
        return 0;
    }

    @Override
    protected PolygonalDetector create(AdaptableInterval newMaxCheck, double newThreshold, int newMaxIter, EventHandler newHandler) {
        return new PolygonalDetector(newMaxCheck, newThreshold, newMaxIter, newHandler, body, (Polygonal) zone, margin);
    }
}
