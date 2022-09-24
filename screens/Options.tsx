import {Image, View, TouchableOpacity} from 'react-native';
import React, {useState} from 'react';
import {styles} from "./TabTwoScreen";
import * as Progress from 'react-native-progress';
import * as Sharing from "expo-sharing";
import uploadToAnonymousFilesAsync from "anonymous-files";
import * as MediaLibrary from "expo-media-library";
import * as FileSystem from "expo-file-system";


export default function Options(
  props: {
    makeAlert: (s: string, s2: string) => void, undo: () => void, redo: () => void, restore: () => void,
    disSave: boolean, disUndo: boolean, disRedo: boolean, disRestore: boolean, curr: string, platform: string
  }) {

  const [loadingSave, setLoadingSave] = useState<boolean>(false);

  /**
   * shares the image
   * web is not supported
   */
  async function share() {
    if (!(await Sharing.isAvailableAsync())) {
      // TODO: something about ssl certificates and anonymousfiles on firefox w/ https
      let remoteUri = await uploadToAnonymousFilesAsync('data:image/jpeg;base64,' + props.curr);
      props.makeAlert('File shared', `Image shared at ${remoteUri}`);
    } else {
      const path: string = await save();
      await Sharing.shareAsync(path).then(() => {
        console.log("Image shared");
      });
    }
  }

  /**
   * downloads image
   */
  async function download() {
    if (props.platform === 'web') {
      let link = document.createElement("a");
      link.download = "download.png";
      link.href = 'data:image/jpeg;base64,' + props.curr;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      console.log("Image downloaded - web")
    } else {
      const path: string = await save();
      MediaLibrary.saveToLibraryAsync(path).then(() => {
        console.log("Image downloaded - mobile");
      });
    }
  }

  // helper method that saves to Expo file system
  async function save() {
    setLoadingSave(true);
    const path: string = FileSystem.cacheDirectory + `download.png`
    await FileSystem.writeAsStringAsync(
      path,
      props.curr,
      {encoding: FileSystem.EncodingType.Base64}
    ).then(res => {
      console.log(res);
      FileSystem.getInfoAsync(path, {size: true, md5: true}).then(file => {
        console.log("File ", file);
      })
    }).catch(err => {
      console.log("err", err);
    });
    setLoadingSave(false);
    return path;
  }

  return (
    <View>
      <View style={styles.rowContainer}>
        <TouchableOpacity
          onPress={download}
          style={props.disSave || loadingSave ? styles.disabledButton : styles.button}
          disabled={props.disSave || loadingSave}>
          <Image source={require('../assets/images/download.png')} style={styles.buttonImage}/>
        </TouchableOpacity>
        <TouchableOpacity
          onPress={share}
          style={props.disSave || loadingSave ? styles.disabledButton : styles.button}
          disabled={props.disSave || loadingSave}>
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
          style={props.disRedo ? styles.disabledButton : styles.button}
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
        {loadingSave && <Progress.Bar width={350} indeterminate={true}/>}
      </View>
    </View>
  );
}
