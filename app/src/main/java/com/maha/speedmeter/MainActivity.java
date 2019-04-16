package com.maha.speedmeter;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.View;
import android.widget.Chronometer;
import android.widget.TextView;

import com.gc.materialdesign.widgets.Dialog;
import com.github.anastr.speedviewlib.SpeedView;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.gson.Gson;
import com.maha.speedmeter.model.Data;
import com.maha.speedmeter.service.GpsServices;
import com.melnykov.fab.FloatingActionButton;

import java.util.Locale;

import permission.auron.com.marshmallowpermissionhelper.ActivityManagePermission;
import permission.auron.com.marshmallowpermissionhelper.PermissionResult;
import permission.auron.com.marshmallowpermissionhelper.PermissionUtils;

public class MainActivity extends ActivityManagePermission implements LocationListener, GpsStatus.Listener, PermissionResult {

   private LocationManager mLocationManager;

   private Data.OnGpsServiceUpdate onGpsServiceUpdate;
   private static Data data;
   private SharedPreferences sharedPreferences;
   private static final int REQUEST_CHECK_SETTINGS = 0x1;
   public static GoogleApiClient mGoogleApiClient;
   private static String[] PERMISSIONS_LOCATION = { Manifest.permission.ACCESS_FINE_LOCATION };
   private SpeedView speedView;
   private boolean firstfix;


   private TextView accuracy;
   private TextView maxSpeed;
   private TextView averageSpeed;
   private TextView distance;
   private Chronometer time;
   private FloatingActionButton fab;
   private FloatingActionButton refresh;

   @Override
   protected void onCreate( Bundle savedInstanceState ) {
      super.onCreate( savedInstanceState );
      setContentView( R.layout.activity_main );

      askPermission();
      mLocationManager = ( LocationManager ) this.getSystemService( Context.LOCATION_SERVICE );
      sharedPreferences = PreferenceManager.getDefaultSharedPreferences( getApplicationContext() );

      init();
   }

   private void init() {

      accuracy = findViewById( R.id.accuracy );
      maxSpeed = findViewById( R.id.maxSpeed );
      averageSpeed = findViewById( R.id.averageSpeed );
      distance = findViewById( R.id.distance );
      time = findViewById( R.id.time );
      fab = findViewById( R.id.fab );
      refresh = findViewById( R.id.refresh );
      speedView = findViewById( R.id.speedView );

      refresh.setVisibility( View.INVISIBLE );
      fab.setVisibility( View.INVISIBLE );

      data = new Data( onGpsServiceUpdate );

      initGoogleAPIClient();

      checkGPSUpdate();

   }

   private void checkGPSUpdate() {

      onGpsServiceUpdate = new Data.OnGpsServiceUpdate() {
         @Override
         public void update() {
            double maxSpeedTemp = data.getMaxSpeed();
            double distanceTemp = data.getDistance();
            double averageTemp;
            if( sharedPreferences.getBoolean( "auto_average", false ) ) {
               averageTemp = data.getAverageSpeedMotion();
            } else {
               averageTemp = data.getAverageSpeed();
            }

            String speedUnits;
            String distanceUnits;
            if( sharedPreferences.getBoolean( "miles_per_hour", false ) ) {
               maxSpeedTemp *= 0.62137119;
               distanceTemp = distanceTemp / 1000.0 * 0.62137119;
               averageTemp *= 0.62137119;
               speedUnits = "mi/h";
               distanceUnits = "mi";
            } else {
               speedUnits = "km/h";
               if( distanceTemp <= 1000.0 ) {
                  distanceUnits = "m";
               } else {
                  distanceTemp /= 1000.0;
                  distanceUnits = "km";
               }
            }

            SpannableString s = new SpannableString( String.format( "%.0f %s", maxSpeedTemp, speedUnits ) );
            s.setSpan( new RelativeSizeSpan( 0.5f ), s.length() - speedUnits.length() - 1, s.length(), 0 );
            maxSpeed.setText( s );

            s = new SpannableString( String.format( "%.0f %s", averageTemp, speedUnits ) );
            s.setSpan( new RelativeSizeSpan( 0.5f ), s.length() - speedUnits.length() - 1, s.length(), 0 );
            averageSpeed.setText( s );

            s = new SpannableString( String.format( "%.3f %s", distanceTemp, distanceUnits ) );
            s.setSpan( new RelativeSizeSpan( 0.5f ), s.length() - distanceUnits.length() - 1, s.length(), 0 );
            distance.setText( s );
         }
      };
   }


   /* Initiate Google API Client  */
   public void initGoogleAPIClient() {
      try {
         //Without Google API Client Auto Location Dialog will not work
         mGoogleApiClient = new GoogleApiClient.Builder( MainActivity.this )
                 .addApi( LocationServices.API ).build();
         mGoogleApiClient.connect();
      } catch( Exception e ) {
         e.printStackTrace();
      }
   }

   public static Data getData() {
      return data;
   }

   @Override
   protected void onResume() {
      super.onResume();
      firstfix = true;

      if( checkPermissionGranted() ) {

         checkPermissions();
         if( !data.isRunning() ) {
            Gson gson = new Gson();
            String json = sharedPreferences.getString( "data", "" );
            data = gson.fromJson( json, Data.class );
         }
         if( data == null ) {
            data = new Data( onGpsServiceUpdate );
         } else {
            data.setOnGpsServiceUpdate( onGpsServiceUpdate );
         }


         if( mLocationManager.getAllProviders().indexOf( LocationManager.GPS_PROVIDER ) >= 0 ) {
            mLocationManager.requestLocationUpdates( LocationManager.GPS_PROVIDER, 500, 0, this );
         } else {
            Log.w( "MainActivity", "No GPS location provider found. GPS data display will not be available." );
         }

         if( !mLocationManager.isProviderEnabled( LocationManager.GPS_PROVIDER ) ) {
            showGpsDisabledDialog();
         }

         mLocationManager.addGpsStatusListener( this );
      }
   }

   @Override
   public void onGpsStatusChanged( int event ) {
      switch( event ) {
         case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
            if( ActivityCompat.checkSelfPermission( this, Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED ) {
               if( ActivityCompat.shouldShowRequestPermissionRationale( MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION ) ) {

                  AlertDialog.Builder alertDialog = new AlertDialog.Builder( MainActivity.this );
                  alertDialog.setMessage( "Allow to access to your location" );

                  // On pressing Settings button
                  alertDialog.setPositiveButton( "Yes",
                          new DialogInterface.OnClickListener() {
                             public void onClick( DialogInterface dialog, int which ) {
                                ActivityCompat.requestPermissions( MainActivity.this, PERMISSIONS_LOCATION, 0 );
                             }
                          } );

                  // on pressing cancel button
                  alertDialog.setNegativeButton( "No",
                          new DialogInterface.OnClickListener() {
                             public void onClick( DialogInterface dialog, int which ) {
                                dialog.cancel();
                             }
                          } );

                  // Showing Alert Message
                  alertDialog.show();

               } else {
                  // Contact permissions have not been granted yet. Request them directly.
                  ActivityCompat.requestPermissions( MainActivity.this, PERMISSIONS_LOCATION, 0 );
               }
            }
            GpsStatus gpsStatus = mLocationManager.getGpsStatus( null );
            int satsInView = 0;
            int satsUsed = 0;
            Iterable<GpsSatellite> sats = gpsStatus.getSatellites();
            for( GpsSatellite sat : sats ) {
               satsInView++;
               if( sat.usedInFix() ) {
                  satsUsed++;
               }
            }
            //  satellite.setText( String.valueOf( satsUsed ) + "/" + String.valueOf( satsInView ) );
            if( satsUsed == 0 ) {
               fab.setImageDrawable( getResources().getDrawable( R.drawable.ic_action_play ) );
               data.setRunning( false );

               stopService( new Intent( getBaseContext(), GpsServices.class ) );
               fab.setVisibility( View.INVISIBLE );
               refresh.setVisibility( View.INVISIBLE );
               accuracy.setText( "" );
               firstfix = true;
            }
            break;

         case GpsStatus.GPS_EVENT_STOPPED:
            if( !mLocationManager.isProviderEnabled( LocationManager.GPS_PROVIDER ) ) {
               showGpsDisabledDialog();
            }
            break;
         case GpsStatus.GPS_EVENT_FIRST_FIX:
            break;
      }
   }

   @Override
   public void onLocationChanged( Location location ) {
      if( location.hasAccuracy() ) {
         double acc = location.getAccuracy();
         String units;
         if( sharedPreferences.getBoolean( "miles_per_hour", false ) ) {
            units = "ft";
            acc *= 3.28084;
         } else {
            units = "m";
         }
         SpannableString s = new SpannableString( String.format( "%.0f %s", acc, units ) );
         s.setSpan( new RelativeSizeSpan( 0.75f ), s.length() - units.length() - 1, s.length(), 0 );
         accuracy.setText( s );

         if( firstfix ) {
            fab.setVisibility( View.VISIBLE );
            if( !data.isRunning() && !TextUtils.isEmpty( maxSpeed.getText() ) ) {
               refresh.setVisibility( View.VISIBLE );
            }
            firstfix = false;
         }
      } else {
         firstfix = true;
      }

      if( location.hasSpeed() ) {
         double speed = location.getSpeed() * 3.6;
         String units;
         if( sharedPreferences.getBoolean( "miles_per_hour", false ) ) { // Convert to MPH
            speed *= 0.62137119;
            units = "mi/h";
         } else {
            units = "km/h";
         }
         SpannableString s = new SpannableString( String.format( Locale.ENGLISH, "%.0f %s", speed, units ) );
         s.setSpan( new RelativeSizeSpan( 0.25f ), s.length() - units.length() - 1, s.length(), 0 );
         speedView.speedTo( ( float ) speed );

      } else {
         speedView.speedTo( 0 );
      }
   }

   @Override
   public void onStatusChanged( String s, int i, Bundle bundle ) {

   }

   @Override
   public void onProviderEnabled( String s ) {

   }

   @Override
   public void onProviderDisabled( String s ) {

   }

   public void showGpsDisabledDialog() {
      Dialog dialog = new Dialog( this, getResources().getString( R.string.gps_disabled ), getResources().getString( R.string.please_enable_gps ) );

      dialog.setOnAcceptButtonClickListener( new View.OnClickListener() {
         @Override
         public void onClick( View view ) {
            startActivity( new Intent( "android.settings.LOCATION_SOURCE_SETTINGS" ) );
         }
      } );
      dialog.show();
   }

   /* Check Location Permission for Marshmallow Devices */
   private void checkPermissions() {
      try {
         if( Build.VERSION.SDK_INT >= 23 ) {
            if( ContextCompat.checkSelfPermission( MainActivity.this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION )
                    != PackageManager.PERMISSION_GRANTED ) {
               // requestLocationPermission();
            } else
               showSettingDialog();
         } else
            showSettingDialog();
      } catch( Exception e ) {
         e.printStackTrace();
      }
   }

   /* Show Location Access Dialog */
   private void showSettingDialog() {
      try {
         LocationRequest locationRequest = LocationRequest.create();
         locationRequest.setPriority( LocationRequest.PRIORITY_HIGH_ACCURACY );//Setting priotity of Location request to high
         locationRequest.setInterval( 30 * 1000 );
         locationRequest.setFastestInterval( 5 * 1000 );//5 sec Time interval for location update
         LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                 .addLocationRequest( locationRequest );

         builder.setAlwaysShow( true ); //this is the key ingredient to show dialog always when GPS is off

         PendingResult<LocationSettingsResult> result = LocationServices.SettingsApi.checkLocationSettings( mGoogleApiClient, builder.build() );
         result.setResultCallback( new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult( @NonNull LocationSettingsResult result ) {
               final Status status = result.getStatus();
               final LocationSettingsStates state = result.getLocationSettingsStates();
               switch( status.getStatusCode() ) {
                  case LocationSettingsStatusCodes.SUCCESS:
                     // All location settings are satisfied. The client can initialize location
                     // requests here.
                     break;
                  case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                     // Location settings are not satisfied. But could be fixed by showing the user
                     // retailer_radio_select dialog.
                     try {
                        // Show the dialog by calling startResolutionForResult(),
                        // and check the result in onActivityResult().
                        status.startResolutionForResult( MainActivity.this, REQUEST_CHECK_SETTINGS );
                     } catch( IntentSender.SendIntentException e ) {
                        e.printStackTrace();
                        // Ignore the error.
                     }
                     break;
                  case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                     // Location settings are not satisfied. However, we have no way to fix the
                     // settings so we won't show the dialog.
                     break;
               }
            }
         } );
      } catch( Exception e ) {
         e.printStackTrace();
      }
   }

   @Override
   public void permissionGranted() {

   }

   @Override
   public void permissionDenied() {
      try {

         final Dialog dialog = new Dialog( this, getResources().getString( R.string.app_name ), getResources().getString( R.string.please_enable_gps ) );

         dialog.setOnCancelButtonClickListener( new View.OnClickListener() {
            @Override
            public void onClick( View view ) {
               dialog.dismiss();
            }
         } );
         dialog.setOnAcceptButtonClickListener( new View.OnClickListener() {
            @Override
            public void onClick( View view ) {
               askPermission();
            }
         } );
         dialog.show();
      } catch( Exception e ) {
         e.printStackTrace();
      }
   }

   @Override
   public void permissionForeverDenied() {
      try {
         final Dialog dialog = new Dialog( this, getResources().getString( R.string.app_name ), getResources().getString( R.string.please_enable_gps ) );

         dialog.setOnAcceptButtonClickListener( new View.OnClickListener() {
            @Override
            public void onClick( View view ) {
               startActivity( new Intent( "android.settings.LOCATION_SOURCE_SETTINGS" ) );
            }
         } );
         dialog.show();
      } catch( Exception e ) {
         e.printStackTrace();
      }
   }

   private void askPermission() {
      try {
         askCompactPermissions( new String[]{ PermissionUtils.Manifest_ACCESS_FINE_LOCATION }, this );
      } catch( Exception e ) {
         e.printStackTrace();
      }
   }

   public boolean checkPermissionGranted() {
      boolean isGranted = false;
      try {
         isGranted = isPermissionsGranted( this, new String[]{ PermissionUtils.Manifest_ACCESS_FINE_LOCATION } );
      } catch( Exception e ) {
         e.printStackTrace();
      }
      return isGranted;
   }

   @Override
   protected void onPause() {
      super.onPause();
      mLocationManager.removeUpdates( this );
      mLocationManager.removeGpsStatusListener( this );
      SharedPreferences.Editor prefsEditor = sharedPreferences.edit();
      Gson gson = new Gson();
      String json = gson.toJson( data );
      prefsEditor.putString( "data", json );
      prefsEditor.commit();
   }

   @Override
   public void onDestroy() {
      super.onDestroy();
      stopService( new Intent( getBaseContext(), GpsServices.class ) );
   }

   public void onFabClick( View view ) {
      if( !data.isRunning() ) {
         fab.setImageDrawable( getResources().getDrawable( R.drawable.ic_action_pause ) );
         data.setRunning( true );
         time.setBase( SystemClock.elapsedRealtime() - data.getTime() );
         time.start();
         data.setFirstTime( true );
         startService( new Intent( getBaseContext(), GpsServices.class ) );
         refresh.setVisibility( View.INVISIBLE );
      } else {
         fab.setImageDrawable( getResources().getDrawable( R.drawable.ic_action_play ) );
         data.setRunning( false );
         stopService( new Intent( getBaseContext(), GpsServices.class ) );
         refresh.setVisibility( View.VISIBLE );
      }
   }

   public void onRefreshClick( View view ) {
      resetData();
      stopService( new Intent( getBaseContext(), GpsServices.class ) );
   }

   public void resetData() {
      fab.setImageDrawable( getResources().getDrawable( R.drawable.ic_action_play ) );
      refresh.setVisibility( View.INVISIBLE );
      time.stop();
      maxSpeed.setText( "" );
      averageSpeed.setText( "" );
      distance.setText( "" );
      time.setText( "00:00:00" );
      data = new Data( onGpsServiceUpdate );
   }

}
