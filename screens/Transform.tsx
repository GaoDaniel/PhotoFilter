import {Image, View, TouchableOpacity} from 'react-native';
import React from 'react';
import {styles} from "./TabTwoScreen";
import * as Progress from 'react-native-progress';


export default function Options(
    props: {download: () => void, share: () => void, undo: () => void, redo: () => void, restore: () => void,
      disSave: boolean, disUndo: boolean, disRedo: boolean, disRestore : boolean, loading: boolean}) {

  return(
    <View>
      <View style={styles.rowContainer}>
        <TouchableOpacity
            onPress={props.download}
            style={props.disSave ? styles.disabledButton : styles.button}
            disabled={props.disSave}>
          <Image source={require('../assets/images/download.png')} style={styles.buttonImage}/>
        </TouchableOpacity>
        <TouchableOpacity
            onPress={props.share}
            style={props.disSave ? styles.disabledButton : styles.button}
            disabled={props.disSave}>
          <Image source={require('../assets/images/share.png')} style={styles.buttonImage}/>
        </TouchableOpacity>
        <TouchableOpacity
            onPress={props.undo}
            style={props.disUndo ? styles.disabledButton : styles.button}
            disabled={props.disUndo}>
          <Image source={require('../assets/images/undo.png')} style={styles.buttonImage}/>
        </TouchableOpacity>
        <TouchableOpacity
            onPress={props.redo}
            style={props.disRedo ? styles.disabledButton: styles.button }
            disabled={props.disRedo}>
          <Image source={require('../assets/images/redo.png')} style={styles.buttonImage}/>
        </TouchableOpacity>
        <TouchableOpacity
            onPress={props.restore}
            style={props.disRestore ? styles.disabledButton : styles.button}
            disabled={props.disRestore}>
          <Image source={require('../assets/images/restore.png')} style={styles.buttonImage}/>
        </TouchableOpacity>
      </View>
      <View style={styles.rowContainer}>
        {props.loading && <Progress.Bar width={350} indeterminate={props.loading}/>}
      </View>
    </View>
);
}
