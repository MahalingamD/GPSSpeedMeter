package com.maha.speedmeter.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

import com.maha.speedmeter.MainActivity;
import com.maha.speedmeter.R;
import com.maha.speedmeter.model.Data;

import static com.maha.speedmeter.R.*;

public class GpsServices extends Service implements LocationListener, GpsStatus.Listener {
   private LocationManager mLocationManager;

   Location lastlocation = new Location( "last" );
   Data data;

   double currentLon = 0;
   double currentLat = 0;
   double lastLon = 0;
   double lastLat = 0;

   PendingIntent contentIntent;
   
   private static final String NOTIFICATION_Service_CHANNEL_ID = "service_channel";


   @Override
   public void onCreate() {

      Intent notificationIntent = new Intent( this, MainActivity.class );
      notificationIntent.setFlags( Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP );
      contentIntent = PendingIntent.getActivity(
              this, 0, notificationIntent, 0 );


      //startInForeground( false );

      mLocationManager = ( LocationManager ) this.getSystemService( Context.LOCATION_SERVICE );
      mLocationManager.addGpsStatusListener( this );
      mLocationManager.requestLocationUpdates( LocationManager.GPS_PROVIDER, 500, 0, this );
   }

   @Override
   public void onLocationChanged( Location location ) {
      data = MainActivity.getData();
      if( data.isRunning() ) {
         currentLat = location.getLatitude();
         currentLon = location.getLongitude();

         if( data.isFirstTime() ) {
            lastLat = currentLat;
            lastLon = currentLon;
            data.setFirstTime( false );
         }

         lastlocation.setLatitude( lastLat );
         lastlocation.setLongitude( lastLon );
         double distance = lastlocation.distanceTo( location );

         if( location.getAccuracy() < distance ) {
            data.addDistance( distance );

            lastLat = currentLat;
            lastLon = currentLon;
         }

         if( location.hasSpeed() ) {
            data.setCurSpeed( location.getSpeed() * 3.6 );
            if( location.getSpeed() == 0 ) {
               new isStillStopped().execute();
            }
         }
         data.update();
         //  updateNotification( true );
         //  startInForeground( true );
      }
   }


   @Override
   public int onStartCommand( Intent intent, int flags, int startId ) {
      // If we get killed, after returning from here, restart
      return START_STICKY;
   }

   @Override
   public IBinder onBind( Intent intent ) {
      // We don't provide binding, so return null
      return null;
   }

   /* Remove the locationlistener updates when Services is stopped */
   @Override
   public void onDestroy() {
      mLocationManager.removeUpdates( this );
      mLocationManager.removeGpsStatusListener( this );
      stopForeground( true );
   }

   @Override
   public void onGpsStatusChanged( int event ) {
   }

   @Override
   public void onProviderDisabled( String provider ) {
   }

   @Override
   public void onProviderEnabled( String provider ) {
   }

   @Override
   public void onStatusChanged( String provider, int status, Bundle extras ) {
   }

   class isStillStopped extends AsyncTask<Void, Integer, String> {
      int timer = 0;

      @Override
      protected String doInBackground( Void... unused ) {
         try {
            while( data.getCurSpeed() == 0 ) {
               Thread.sleep( 1000 );
               timer++;
            }
         } catch( InterruptedException t ) {
            return ( "The sleep operation failed" );
         }
         return ( "return object when task is finished" );
      }

      @Override
      protected void onPostExecute( String message ) {
         data.setTimeStopped( timer );
      }
   }

   private void startInForeground( boolean b ) {
      int icon = mipmap.ic_launcher;

      if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
         icon = mipmap.ic_launcher;
      }

      Intent notificationIntent = new Intent( this, MainActivity.class );
      PendingIntent pendingIntent = PendingIntent.getActivity( this, 0, notificationIntent, 0 );
      NotificationCompat.Builder builder = new NotificationCompat.Builder( this )
              .setSmallIcon( icon )
              .setContentIntent( pendingIntent )
              .setContentTitle( getApplicationContext().getString( string.app_name ) );

      if( b ) {
         builder.setContentText( String.format( getString( string.notification ), data.getMaxSpeed(), data.getDistance() ) );
      } else {
         builder.setContentText( String.format( getString( string.notification ), '-', '-' ) );
      }
      Notification notification = builder.build();
      if( Build.VERSION.SDK_INT >= 26 ) {
         NotificationChannel channel = new NotificationChannel( NOTIFICATION_Service_CHANNEL_ID, "Sync Service", NotificationManager.IMPORTANCE_HIGH );
         channel.setDescription( "Service Name" );
         NotificationManager notificationManager = ( NotificationManager ) getSystemService( Context.NOTIFICATION_SERVICE );
         notificationManager.createNotificationChannel( channel );

         Notification.Builder anot = new Notification.Builder( this, NOTIFICATION_Service_CHANNEL_ID );
         anot.setContentTitle( getApplicationContext().getString( string.app_name ) )
                 .setSmallIcon( icon )
                 .setContentIntent( pendingIntent );
         if( b ) {
            anot.setContentText( String.format( getString( string.notification ), data.getMaxSpeed(), data.getDistance() ) );
         } else {
            anot.setContentText( String.format( getString( string.notification ), "-", "-" ) );
         }
         notification = anot.build();
      }

      startForeground( 121, notification );
   }
}
