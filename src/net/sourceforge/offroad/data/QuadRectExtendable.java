/** 
   OffRoad
   Copyright (C) 2016 Christian Foltin

   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; either version 3 of the License, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program; if not, write to the Free Software Foundation,
   Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
*/

package net.sourceforge.offroad.data;

import net.osmand.data.LatLon;
import net.osmand.data.QuadRect;

/**
 * @author foltin
 * @date 08.04.2016
 */
public class QuadRectExtendable extends QuadRect {
	
	public QuadRectExtendable(LatLon pLatLon) {
		double lat = pLatLon.getLatitude();
		double lon = pLatLon.getLongitude();
		left = lat;
		right = lat;
		top = lon;
		bottom = lon;
	}
	
	public void insert(LatLon pLatLon){
		double lat = pLatLon.getLatitude();
		if(left > lat){
			left = lat;
		}
		if(right < lat){
			right = lat;
		}
		double lon = pLatLon.getLongitude();
		if(top > lon){
			top = lon;
		}
		if(bottom < lon){
			bottom = lon;
		}
	}

	public LatLon getCenter() {
		return new LatLon((left+right)/2, (top+bottom)/2);
	}
	public LatLon getTopLeft() {
		return new LatLon(left, top);
	}
	public LatLon getBottomRight() {
		return new LatLon(right, bottom);
	}
}
